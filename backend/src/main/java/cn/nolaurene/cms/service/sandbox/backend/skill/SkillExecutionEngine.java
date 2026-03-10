package cn.nolaurene.cms.service.sandbox.backend.skill;

import cn.nolaurene.cms.common.dto.skill.*;
import cn.nolaurene.cms.common.sandbox.worker.resp.shell.ShellCommandResult;
import cn.nolaurene.cms.dal.entity.SkillExecutionLogDO;
import cn.nolaurene.cms.dal.entity.SkillInfoDO;
import cn.nolaurene.cms.dal.mapper.SkillExecutionLogMapper;
import cn.nolaurene.cms.dal.mapper.SkillInfoMapper;
import cn.nolaurene.cms.exception.skill.SkillDependencyException;
import cn.nolaurene.cms.exception.skill.SkillExecutionException;
import cn.nolaurene.cms.exception.skill.SkillNotFoundException;
import cn.nolaurene.cms.service.sandbox.worker.shell.ShellService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill执行引擎
 * 所有命令都通过 Shell 工具在沙箱中执行
 *
 * @author nolaurence
 */
@Slf4j
@Service
public class SkillExecutionEngine {

    private final ShellService shellService;
    private final SkillInfoMapper skillInfoMapper;
    private final SkillExecutionLogMapper executionLogMapper;

    /**
     * Skill缓存
     */
    private final Map<String, SkillDefinitionDTO> skillCache = new ConcurrentHashMap<>();

    public SkillExecutionEngine(ShellService shellService,
                                SkillInfoMapper skillInfoMapper,
                                SkillExecutionLogMapper executionLogMapper) {
        this.shellService = shellService;
        this.skillInfoMapper = skillInfoMapper;
        this.executionLogMapper = executionLogMapper;
    }

    /**
     * 执行Skill
     *
     * @param request 执行请求
     * @return 执行结果
     */
    public SkillExecutionResult execute(SkillExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        SkillExecutionResult result = new SkillExecutionResult();
        result.setSkillId(request.getSkillId());

        try {
            // 1. 加载Skill定义
            SkillDefinitionDTO skill = loadSkill(request.getSkillId());
            if (skill == null) {
                throw new SkillNotFoundException("Skill not found: " + request.getSkillId());
            }

            // 2. 验证依赖
            validateDependencies(skill, request.getSessionId());

            // 3. 获取要执行的工具
            ToolDefinition tool = resolveTool(skill, request.getParams());
            if (tool == null) {
                throw new SkillExecutionException("No matching tool found for skill: " + request.getSkillId());
            }

            // 4. 渲染命令模板
            String command = renderCommand(tool.getCommand(), request.getParams());

            // 5. 通过Shell沙箱执行命令
            String workingDir = StringUtils.defaultIfEmpty(request.getWorkingDir(), "/workspace");
            ShellCommandResult shellResult = shellService.execCommand(
                    request.getSessionId(),
                    workingDir,
                    command
            );

            // 6. 构建结果
            result.setStatus(shellResult.getStatus());
            result.setOutput(shellResult.getOutput());
            result.setDurationMs(System.currentTimeMillis() - startTime);

            log.info("Skill executed successfully: {} in {}ms", request.getSkillId(), result.getDurationMs());

        } catch (SkillNotFoundException | SkillDependencyException | SkillExecutionException e) {
            log.error("Skill execution failed: {}", request.getSkillId(), e);
            result.setStatus("FAILED");
            result.setError(e.getMessage());
            result.setDurationMs(System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Skill execution failed with unexpected error: {}", request.getSkillId(), e);
            result.setStatus("FAILED");
            result.setError("Unexpected error: " + e.getMessage());
            result.setDurationMs(System.currentTimeMillis() - startTime);
        }

        // 7. 记录执行日志
        saveExecutionLog(request, result);

        return result;
    }

    /**
     * 加载Skill定义（从缓存或数据库）
     */
    private SkillDefinitionDTO loadSkill(String skillId) {
        return skillCache.computeIfAbsent(skillId, this::loadSkillFromDB);
    }

    /**
     * 从数据库加载Skill定义
     */
    private SkillDefinitionDTO loadSkillFromDB(String skillId) {
        SkillInfoDO skillInfo = skillInfoMapper.selectBySkillId(skillId);
        if (skillInfo == null) {
            return null;
        }

        SkillDefinitionDTO dto = new SkillDefinitionDTO();
        dto.setSkillId(skillInfo.getSkillId());
        dto.setName(skillInfo.getName());
        dto.setVersion(skillInfo.getVersion());
        dto.setAuthor(skillInfo.getAuthor());
        dto.setDescription(skillInfo.getDescription());
        dto.setCategory(skillInfo.getCategory());
        dto.setPriority(skillInfo.getPriority());
        dto.setUserId(skillInfo.getUserId());

        // 解析JSON字段
        if (StringUtils.isNotBlank(skillInfo.getTriggers())) {
            dto.setTriggers(JSON.parseArray(skillInfo.getTriggers(), TriggerConfig.class));
        }
        if (StringUtils.isNotBlank(skillInfo.getTools())) {
            dto.setTools(JSON.parseArray(skillInfo.getTools(), ToolDefinition.class));
        }
        if (StringUtils.isNotBlank(skillInfo.getRequires())) {
            dto.setRequires(JSON.parseObject(skillInfo.getRequires(), RequiresConfig.class));
        }
        if (StringUtils.isNotBlank(skillInfo.getOsSupport())) {
            dto.setOsSupport(JSON.parseArray(skillInfo.getOsSupport(), String.class));
        }

        return dto;
    }

    /**
     * 验证Skill依赖
     * 通过Shell沙箱检查依赖是否存在
     */
    private void validateDependencies(SkillDefinitionDTO skill, String sessionId) {
        RequiresConfig requires = skill.getRequires();
        if (requires == null) {
            return;
        }

        List<String> missingBins = new ArrayList<>();

        // 检查二进制依赖
        if (requires.getBins() != null) {
            for (String bin : requires.getBins()) {
                String checkCmd = String.format("which %s 2>/dev/null || command -v %s 2>/dev/null", bin, bin);
                try {
                    ShellCommandResult result = shellService.execCommand(sessionId, "/", checkCmd);
                    if (result.getReturncode() != 0 || StringUtils.isBlank(result.getOutput())) {
                        missingBins.add(bin);
                    }
                } catch (Exception e) {
                    log.warn("Failed to check binary dependency: {}", bin, e);
                    missingBins.add(bin);
                }
            }
        }

        if (!missingBins.isEmpty()) {
            throw new SkillDependencyException(
                    "Missing required binaries: " + String.join(", ", missingBins)
            );
        }

        // 检查环境变量（仅警告，不阻止执行）
        if (requires.getEnv() != null) {
            for (Map.Entry<String, String> entry : requires.getEnv().entrySet()) {
                String checkCmd = String.format("echo $%s", entry.getKey());
                try {
                    ShellCommandResult result = shellService.execCommand(sessionId, "/", checkCmd);
                    String value = result.getOutput() != null ? result.getOutput().trim() : "";
                    if (StringUtils.isBlank(value)) {
                        log.warn("Environment variable {} is not set", entry.getKey());
                    }
                } catch (Exception e) {
                    log.warn("Failed to check environment variable: {}", entry.getKey(), e);
                }
            }
        }
    }

    /**
     * 解析要执行的工具
     */
    private ToolDefinition resolveTool(SkillDefinitionDTO skill, Map<String, Object> params) {
        if (skill.getTools() == null || skill.getTools().isEmpty()) {
            return null;
        }

        // 如果只有一个工具，直接返回
        if (skill.getTools().size() == 1) {
            return skill.getTools().get(0);
        }

        // 根据参数匹配工具
        for (ToolDefinition tool : skill.getTools()) {
            if (matchesTool(tool, params)) {
                return tool;
            }
        }

        // 默认返回第一个工具
        return skill.getTools().get(0);
    }

    /**
     * 检查参数是否匹配工具
     */
    private boolean matchesTool(ToolDefinition tool, Map<String, Object> params) {
        if (tool.getParameters() == null || tool.getParameters().isEmpty()) {
            return true;
        }

        for (String paramKey : tool.getParameters().keySet()) {
            if (params == null || !params.containsKey(paramKey)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 渲染命令模板
     * 支持变量替换：${var}, {{var}}, {var}
     */
    private String renderCommand(String template, Map<String, Object> params) {
        if (StringUtils.isBlank(template)) {
            return "";
        }

        if (params == null || params.isEmpty()) {
            return template;
        }

        String result = template;

        // 替换 ${var} 格式
        result = replaceVariables(result, "\\$\\{([^}]+)\\}", params);

        // 替换 {{var}} 格式
        result = replaceVariables(result, "\\{\\{([^}]+)\\}\\}", params);

        // 替换 {var} 格式（注意不要替换已经被处理的）
        result = replaceVariables(result, "\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}", params);

        return result;
    }

    /**
     * 替换变量
     */
    private String replaceVariables(String template, String pattern, Map<String, Object> params) {
        Pattern p = Pattern.compile(pattern);
        Matcher matcher = p.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = params.get(varName);
            String replacement = value != null ? value.toString() : "";
            // 转义特殊字符
            replacement = replacement.replace("\\", "\\\\").replace("$", "\\$");
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 根据触发器匹配Skill
     *
     * @param input 用户输入
     * @return 匹配的Skill列表，按优先级排序
     */
    public List<SkillDefinitionDTO> matchSkills(String input) {
        List<SkillDefinitionDTO> matchedSkills = new ArrayList<>();

        // 加载所有活跃的Skill
        List<SkillInfoDO> allSkills = skillInfoMapper.selectAllActive();

        for (SkillInfoDO skillInfo : allSkills) {
            SkillDefinitionDTO skill = loadSkill(skillInfo.getSkillId());
            if (skill != null && matchesTrigger(skill, input)) {
                matchedSkills.add(skill);
            }
        }

        // 按优先级排序
        matchedSkills.sort((a, b) -> {
            int priorityA = a.getPriority() != null ? a.getPriority() : 0;
            int priorityB = b.getPriority() != null ? b.getPriority() : 0;
            return Integer.compare(priorityB, priorityA);
        });

        return matchedSkills;
    }

    /**
     * 检查输入是否匹配触发器
     */
    private boolean matchesTrigger(SkillDefinitionDTO skill, String input) {
        if (skill.getTriggers() == null || skill.getTriggers().isEmpty()) {
            return false;
        }

        String lowerInput = input.toLowerCase();

        for (TriggerConfig trigger : skill.getTriggers()) {
            if (trigger.getType() == null || trigger.getPattern() == null) {
                continue;
            }

            switch (trigger.getType().toLowerCase()) {
                case "keyword":
                    if (lowerInput.contains(trigger.getPattern().toLowerCase())) {
                        return true;
                    }
                    break;
                case "regex":
                    try {
                        if (Pattern.matches(trigger.getPattern(), input)) {
                            return true;
                        }
                    } catch (Exception e) {
                        log.warn("Invalid regex pattern: {}", trigger.getPattern(), e);
                    }
                    break;
                case "intent":
                    // 可以接入意图识别模型
                    log.debug("Intent trigger not implemented yet");
                    break;
                default:
                    log.warn("Unknown trigger type: {}", trigger.getType());
            }
        }

        return false;
    }

    /**
     * 保存执行日志
     */
    private void saveExecutionLog(SkillExecutionRequest request, SkillExecutionResult result) {
        try {
            SkillExecutionLogDO logDO = new SkillExecutionLogDO();
            logDO.setSkillId(request.getSkillId());
            logDO.setSessionId(request.getSessionId());
            logDO.setUserId(null); // 可以从上下文获取
            logDO.setInputParams(request.getParams() != null ? JSON.toJSONString(request.getParams()) : null);
            logDO.setOutputResult(result.getOutput());
            logDO.setStatus(result.getStatus());
            logDO.setErrorMessage(result.getError());
            logDO.setDurationMs(result.getDurationMs());
            logDO.setGmtCreate(new Date());

            executionLogMapper.insert(logDO);
        } catch (Exception e) {
            log.error("Failed to save execution log", e);
        }
    }

    /**
     * 刷新Skill缓存
     */
    public void refreshCache() {
        skillCache.clear();
        log.info("Skill cache refreshed");
    }

    /**
     * 刷新指定Skill缓存
     */
    public void refreshCache(String skillId) {
        skillCache.remove(skillId);
        log.info("Skill cache refreshed for: {}", skillId);
    }

    /**
     * 预热缓存
     */
    public void warmUpCache() {
        List<SkillInfoDO> allSkills = skillInfoMapper.selectAllActive();
        for (SkillInfoDO skillInfo : allSkills) {
            loadSkill(skillInfo.getSkillId());
        }
        log.info("Skill cache warmed up with {} skills", allSkills.size());
    }
}
