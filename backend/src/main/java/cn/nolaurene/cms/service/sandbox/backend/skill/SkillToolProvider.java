package cn.nolaurene.cms.service.sandbox.backend.skill;

import cn.nolaurene.cms.common.dto.skill.SkillDefinitionDTO;
import cn.nolaurene.cms.common.dto.skill.ToolDefinition;
import cn.nolaurene.cms.common.dto.skill.TriggerConfig;
import cn.nolaurene.cms.dal.entity.SkillInfoDO;
import cn.nolaurene.cms.dal.mapper.SkillInfoMapper;
import cn.nolaurene.cms.dal.mapper.UserSkillStatusMapper;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Skill 工具提供者
 * 将数据库中的 Skill 转换为 ToolSpecification，供 Agent 使用
 *
 * @author nolaurence
 */
@Slf4j
@Component
public class SkillToolProvider {

    @Resource
    private SkillInfoMapper skillInfoMapper;

    @Resource
    private UserSkillStatusMapper userSkillStatusMapper;

    @Resource
    private SkillExecutionEngine skillExecutionEngine;

    /**
     * Skill 缓存
     */
    private final Map<String, SkillDefinitionDTO> skillCache = new ConcurrentHashMap<>();

    /**
     * Skill 工具名称前缀
     */
    public static final String SKILL_TOOL_PREFIX = "skill_";

    /**
     * 初始化时预热缓存
     */
    @PostConstruct
    public void init() {
        warmUpCache();
    }

    /**
     * 获取所有活跃的 Skill 作为 ToolSpecification 列表
     */
    public List<ToolSpecification> getSkillToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();

        List<SkillInfoDO> activeSkills = skillInfoMapper.selectAllActive();
        for (SkillInfoDO skillInfo : activeSkills) {
            try {
                ToolSpecification spec = convertToToolSpecification(skillInfo);
                if (spec != null) {
                    specs.add(spec);
                }
            } catch (Exception e) {
                log.error("Failed to convert skill {} to ToolSpecification", skillInfo.getSkillId(), e);
            }
        }

        log.info("Loaded {} skill tool specifications", specs.size());
        return specs;
    }

    /**
     * 将 Skill 转换为 ToolSpecification
     */
    private ToolSpecification convertToToolSpecification(SkillInfoDO skillInfo) {
        String toolName = SKILL_TOOL_PREFIX + normalizeToolName(skillInfo.getSkillId());

        // 构建描述
        StringBuilder description = new StringBuilder();
        description.append(skillInfo.getDescription() != null ? skillInfo.getDescription() : skillInfo.getName());
        description.append("\n\nSkill ID: ").append(skillInfo.getSkillId());
        description.append("\nAuthor: ").append(skillInfo.getAuthor());
        description.append("\nVersion: ").append(skillInfo.getVersion());

        // 添加触发器信息到描述
        if (StringUtils.isNotBlank(skillInfo.getTriggers())) {
            List<TriggerConfig> triggers = JSON.parseArray(skillInfo.getTriggers(), TriggerConfig.class);
            if (triggers != null && !triggers.isEmpty()) {
                description.append("\n\nTriggers: ");
                for (TriggerConfig trigger : triggers) {
                    description.append(trigger.getPattern()).append(" ");
                }
            }
        }

        // 构建参数 schema
        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();

        // 解析工具定义，提取参数
        if (StringUtils.isNotBlank(skillInfo.getTools())) {
            List<ToolDefinition> tools = JSON.parseArray(skillInfo.getTools(), ToolDefinition.class);
            if (tools != null && !tools.isEmpty()) {
                ToolDefinition firstTool = tools.get(0);
                if (firstTool.getParameters() != null) {
                    for (Map.Entry<String, Object> entry : firstTool.getParameters().entrySet()) {
                        String paramName = entry.getKey();
                        Object paramDef = entry.getValue();

                        String paramDescription = extractParamDescription(paramDef);
                        schemaBuilder.addStringProperty(paramName, paramDescription);
                    }
                }
            }
        }

        // 添加必需的 session_id 参数
        schemaBuilder.addStringProperty("session_id", "The session ID for shell command execution");

        ToolSpecification.Builder specBuilder = ToolSpecification.builder()
                .name(toolName)
                .description(description.toString())
                .parameters(schemaBuilder.build());

        return specBuilder.build();
    }

    /**
     * 从参数定义中提取描述
     */
    @SuppressWarnings("unchecked")
    private String extractParamDescription(Object paramDef) {
        if (paramDef instanceof String) {
            return (String) paramDef;
        } else if (paramDef instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) paramDef;
            Object desc = paramMap.get("description");
            return desc != null ? desc.toString() : paramMap.get("type") + " parameter";
        }
        return "parameter";
    }

    /**
     * 规范化工具名称（替换特殊字符）
     */
    private String normalizeToolName(String skillId) {
        return skillId.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * 检查工具名称是否是 Skill 工具
     */
    public boolean isSkillTool(String toolName) {
        return toolName != null && toolName.startsWith(SKILL_TOOL_PREFIX);
    }

    /**
     * 从工具名称解析 Skill ID
     */
    public String parseSkillIdFromToolName(String toolName) {
        if (!isSkillTool(toolName)) {
            return null;
        }

        String normalized = toolName.substring(SKILL_TOOL_PREFIX.length());

        // 尝试从缓存中匹配
        for (String skillId : skillCache.keySet()) {
            if (normalizeToolName(skillId).equals(normalized)) {
                return skillId;
            }
        }

        // 如果缓存中没有，尝试从数据库查找
        List<SkillInfoDO> allSkills = skillInfoMapper.selectAllActive();
        for (SkillInfoDO skill : allSkills) {
            if (normalizeToolName(skill.getSkillId()).equals(normalized)) {
                return skill.getSkillId();
            }
        }

        return null;
    }

    /**
     * 获取 Skill 定义
     */
    public SkillDefinitionDTO getSkillDefinition(String skillId) {
        return skillCache.computeIfAbsent(skillId, this::loadSkillFromDB);
    }

    /**
     * 从数据库加载 Skill
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

        if (StringUtils.isNotBlank(skillInfo.getTriggers())) {
            dto.setTriggers(JSON.parseArray(skillInfo.getTriggers(), TriggerConfig.class));
        }
        if (StringUtils.isNotBlank(skillInfo.getTools())) {
            dto.setTools(JSON.parseArray(skillInfo.getTools(), ToolDefinition.class));
        }
        if (StringUtils.isNotBlank(skillInfo.getRequires())) {
            dto.setRequires(JSON.parseObject(skillInfo.getRequires(), cn.nolaurene.cms.common.dto.skill.RequiresConfig.class));
        }
        if (StringUtils.isNotBlank(skillInfo.getOsSupport())) {
            dto.setOsSupport(JSON.parseArray(skillInfo.getOsSupport(), String.class));
        }

        return dto;
    }

    /**
     * 预热缓存
     */
    public void warmUpCache() {
        List<SkillInfoDO> allSkills = skillInfoMapper.selectAllActive();
        for (SkillInfoDO skillInfo : allSkills) {
            loadSkillFromDB(skillInfo.getSkillId());
        }
        log.info("Skill cache warmed up with {} skills", allSkills.size());
    }

    /**
     * 刷新缓存
     */
    public void refreshCache() {
        skillCache.clear();
        warmUpCache();
    }

    /**
     * 刷新指定 Skill 缓存
     */
    public void refreshCache(String skillId) {
        skillCache.remove(skillId);
        loadSkillFromDB(skillId);
    }

    // ==================== 用户级Skill工具获取 ====================

    /**
     * 获取用户启用的所有 Skill 工具
     *
     * @param userId 用户ID
     * @return ToolSpecification 列表
     */
    public List<ToolSpecification> getSkillToolSpecificationsForUser(Long userId) {
        // 获取用户启用的Skill ID列表
        List<String> enabledSkillIds = userSkillStatusMapper.selectEnabledSkillIdsByUserId(userId);

        if (enabledSkillIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<ToolSpecification> specs = new ArrayList<>();

        for (String skillId : enabledSkillIds) {
            SkillDefinitionDTO skill = getSkillDefinition(skillId);
            if (skill != null) {
                try {
                    // 从缓存获取或转换
                    SkillInfoDO skillInfo = skillInfoMapper.selectBySkillId(skillId);
                    if (skillInfo != null && skillInfo.getStatus() != null && skillInfo.getStatus() == 1) {
                        ToolSpecification spec = convertToToolSpecification(skillInfo);
                        if (spec != null) {
                            specs.add(spec);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to convert skill {} to ToolSpecification for user {}", skillId, userId, e);
                }
            }
        }

        log.info("Loaded {} skill tool specifications for user {}", specs.size(), userId);
        return specs;
    }

    /**
     * 根据用户输入匹配用户启用的Skill
     *
     * @param input  用户输入
     * @param userId 用户ID
     * @return 匹配的Skill列表
     */
    public List<SkillDefinitionDTO> matchSkillsForUser(String input, Long userId) {
        List<String> enabledSkillIds = userSkillStatusMapper.selectEnabledSkillIdsByUserId(userId);

        List<SkillDefinitionDTO> matchedSkills = new ArrayList<>();

        for (String skillId : enabledSkillIds) {
            SkillDefinitionDTO skill = getSkillDefinition(skillId);
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
                        if (input.matches(trigger.getPattern())) {
                            return true;
                        }
                    } catch (Exception e) {
                        log.warn("Invalid regex pattern: {}", trigger.getPattern(), e);
                    }
                    break;
                case "intent":
                    log.debug("Intent trigger not implemented yet");
                    break;
                default:
                    log.warn("Unknown trigger type: {}", trigger.getType());
            }
        }

        return false;
    }
}
