package cn.nolaurene.cms.service.sandbox.backend.skill;

import cn.nolaurene.cms.common.dto.skill.*;
import cn.nolaurene.cms.dal.entity.SkillExecutionLogDO;
import cn.nolaurene.cms.dal.entity.SkillInfoDO;
import cn.nolaurene.cms.dal.entity.UserSkillStatusDO;
import cn.nolaurene.cms.dal.mapper.SkillExecutionLogMapper;
import cn.nolaurene.cms.dal.mapper.SkillInfoMapper;
import cn.nolaurene.cms.dal.mapper.UserSkillStatusMapper;
import cn.nolaurene.cms.exception.skill.SkillDependencyException;
import cn.nolaurene.cms.exception.skill.SkillExecutionException;
import cn.nolaurene.cms.exception.skill.SkillNotFoundException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.mybatis.mapper.example.Example;
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

    private final SkillInfoMapper skillInfoMapper;
    private final SkillExecutionLogMapper executionLogMapper;
    private final UserSkillStatusMapper userSkillStatusMapper;
    private final SkillFileStorageService fileStorageService;

    /**
     * Skill缓存
     */
    private final Map<String, SkillDefinitionDTO> skillCache = new ConcurrentHashMap<>();

    public SkillExecutionEngine(SkillInfoMapper skillInfoMapper,
                                SkillExecutionLogMapper executionLogMapper,
                                UserSkillStatusMapper userSkillStatusMapper,
                                SkillFileStorageService fileStorageService) {
        this.skillInfoMapper = skillInfoMapper;
        this.executionLogMapper = executionLogMapper;
        this.userSkillStatusMapper = userSkillStatusMapper;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 执行Skill
     *
     * @param request 执行请求
     * @return 执行结果
     */
    public SkillExecutionResult execute(SkillExecutionRequest request, McpClient mcpClient) {
        long startTime = System.currentTimeMillis();
        SkillExecutionResult result = new SkillExecutionResult();
        result.setSkillId(request.getSkillId());

        try {
            // 1. 加载Skill定义
            SkillDefinitionDTO skill = loadSkill(request.getSkillId());
            if (skill == null) {
                throw new SkillNotFoundException("Skill not found: " + request.getSkillId());
            }

            // 2. 检查用户是否启用了该Skill（如果提供了userId）
            if (request.getUserId() != null) {
                if (!isSkillEnabledForUser(request.getUserId(), request.getSkillId())) {
                    throw new SkillExecutionException("Skill is disabled for user: " + request.getSkillId());
                }
            }

            // 3. 验证依赖
            validateDependencies(skill, request.getSessionId());

            // 5. 渲染命令模板
            String command = String.valueOf(request.getParams().getOrDefault("command", ""));

            if (StringUtils.isBlank(command)) {
                result.setStatus("FAILED");
                result.setError("No command provided");
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // 6. 添加脚本路径环境变量（如果存在scripts目录）
            String scriptsPath = fileStorageService.getSkillScriptsPath(request.getSkillId());
            if (StringUtils.isNotBlank(scriptsPath)) {
                command = "export SKILL_SCRIPTS_PATH=" + scriptsPath + " && " + command;
            }

            // 7. 通过McpClient调用沙箱shell_exec工具执行命令
            Map<String, Object> shellArgs = new LinkedHashMap<>();
            shellArgs.put("id", request.getSessionId());
            shellArgs.put("execDir", "");
            shellArgs.put("command", command);

            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .name("shell_exec")
                    .arguments(JSON.toJSONString(shellArgs))
                    .build();
            ToolExecutionResult toolResult = mcpClient.executeTool(toolRequest);

            // 6. 构建结果
            if (toolResult.isError()) {
                result.setStatus("FAILED");
                result.setError(toolResult.resultText());
            } else {
                result.setStatus("SUCCESS");
                result.setOutput(toolResult.resultText());
            }
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
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        SkillInfoDO skillInfo = skillInfoMapper.selectOneByExample(example).orElse(null);
        if (skillInfo == null) {
            return null;
        }

        SkillDefinitionDTO dto = new SkillDefinitionDTO();
        dto.setSkillId(skillInfo.getSkillId());
        dto.setName(skillInfo.getName());
        dto.setDescription(skillInfo.getDescription());
        dto.setVersion(skillInfo.getVersion());
        dto.setLicense(skillInfo.getLicense());
        dto.setCompatibility(skillInfo.getCompatibility());
        if (StringUtils.isNotBlank(skillInfo.getMetadata())) {
            Map<String, String> metadata = JSON.parseObject(skillInfo.getMetadata(), new TypeReference<Map<String, String>>() {});
            dto.setMetadata(metadata);
            // 从 metadata 中读取 author，符合 Agent Skills 规范
            String author = metadata != null ? metadata.get("author") : null;
            dto.setAuthor(author != null ? author : "anonymous");
        } else {
            dto.setAuthor("anonymous");
        }
        dto.setAllowedTools(skillInfo.getAllowedTools());
        dto.setUserId(skillInfo.getUserId());

        return dto;
    }

    /**
     * 验证Skill依赖
     * 通过Shell沙箱检查依赖是否存在
     */
    private void validateDependencies(SkillDefinitionDTO skill, String sessionId) {
        // 依赖检查已移除 - 遵循 agentskills.io 规范
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
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getStatus, 1)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        example.orderByDesc(SkillInfoDO::getGmtCreate);
        List<SkillInfoDO> allSkills = skillInfoMapper.selectByExample(example);

        for (SkillInfoDO skillInfo : allSkills) {
            SkillDefinitionDTO skill = loadSkill(skillInfo.getSkillId());
            if (skill != null && matchesTrigger(skill, input)) {
                matchedSkills.add(skill);
            }
        }

        return matchedSkills;
    }

    /**
     * 检查输入是否匹配触发器
     */
    private boolean matchesTrigger(SkillDefinitionDTO skill, String input) {
        // 触发器匹配已简化 - 遵循 agentskills.io 规范
        // 可以根据 name 和 description 进行简单匹配
        if (skill.getName() != null && input.toLowerCase().contains(skill.getName().toLowerCase())) {
            return true;
        }
        if (skill.getDescription() != null && input.toLowerCase().contains(skill.getDescription().toLowerCase())) {
            return true;
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
            logDO.setUserId(request.getUserId()); // 从请求中获取用户ID
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
     * 检查用户是否启用了某个Skill
     */
    private boolean isSkillEnabledForUser(Long userId, String skillId) {
        Example<UserSkillStatusDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(UserSkillStatusDO::getUserId, userId)
                .andEqualTo(UserSkillStatusDO::getSkillId, skillId);
        UserSkillStatusDO status = userSkillStatusMapper.selectOneByExample(example).orElse(null);
        // 如果没有记录，默认启用（向后兼容）
        if (status == null) {
            return true;
        }
        return status.getStatus() != null && status.getStatus() == 1;
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
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getStatus, 1)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        example.orderByDesc(SkillInfoDO::getGmtCreate);
        List<SkillInfoDO> allSkills = skillInfoMapper.selectByExample(example);
        for (SkillInfoDO skillInfo : allSkills) {
            loadSkill(skillInfo.getSkillId());
        }
        log.info("Skill cache warmed up with {} skills", allSkills.size());
    }
}
