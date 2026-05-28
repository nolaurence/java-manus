package cn.nolaurene.cms.service.sandbox.backend.skill;

import cn.nolaurene.cms.common.dto.skill.SkillDefinitionDTO;
import cn.nolaurene.cms.common.dto.skill.ToolDefinition;
import cn.nolaurene.cms.common.dto.skill.TriggerConfig;
import cn.nolaurene.cms.dal.entity.SkillInfoDO;
import cn.nolaurene.cms.dal.entity.UserSkillStatusDO;
import cn.nolaurene.cms.dal.mapper.SkillInfoMapper;
import cn.nolaurene.cms.dal.mapper.UserSkillStatusMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.mybatis.mapper.example.Example;
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

        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getStatus, 1)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        example.orderByDesc(SkillInfoDO::getGmtCreate);
        List<SkillInfoDO> activeSkills = skillInfoMapper.selectByExample(example);

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

        // 从 metadata 中读取 author
        String author = "anonymous";
        if (StringUtils.isNotBlank(skillInfo.getMetadata())) {
            Map<String, String> metadata = JSON.parseObject(skillInfo.getMetadata(), new TypeReference<Map<String, String>>() {});
            if (metadata != null && metadata.get("author") != null) {
                author = metadata.get("author");
            }
        }

        // 构建描述
        StringBuilder description = new StringBuilder();
        description.append(skillInfo.getDescription() != null ? skillInfo.getDescription() : skillInfo.getName());
        description.append("\n\nSkill ID: ").append(skillInfo.getSkillId());
        description.append("\nAuthor: ").append(author);
        description.append("\nVersion: ").append(skillInfo.getVersion());

        // 构建参数 schema
        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();

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
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getStatus, 1)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        List<SkillInfoDO> allSkills = skillInfoMapper.selectByExample(example);
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
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        SkillInfoDO skillInfo = skillInfoMapper.selectOneByExample(example).orElse(null);
        if (skillInfo == null) {
            return null;
        }
        return convertToDefinitionDTO(skillInfo);
    }

    private SkillDefinitionDTO convertToDefinitionDTO(SkillInfoDO skillInfo) {
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
     * 预热缓存
     */
    public void warmUpCache() {
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getStatus, 1)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        List<SkillInfoDO> allSkills = skillInfoMapper.selectByExample(example);
        for (SkillInfoDO skillInfo : allSkills) {
            SkillDefinitionDTO skillDefinitionDTO = convertToDefinitionDTO(skillInfo);
            skillCache.put(skillDefinitionDTO.getSkillId(), skillDefinitionDTO);
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
        Example<UserSkillStatusDO> statusExample = new Example<>();
        statusExample.createCriteria()
                .andEqualTo(UserSkillStatusDO::getUserId, userId)
                .andEqualTo(UserSkillStatusDO::getStatus, 1);
        List<String> enabledSkillIds = userSkillStatusMapper.selectByExample(statusExample).stream()
                .map(UserSkillStatusDO::getSkillId)
                .collect(Collectors.toList());

        if (enabledSkillIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<ToolSpecification> specs = new ArrayList<>();

        for (String skillId : enabledSkillIds) {
            SkillDefinitionDTO skill = getSkillDefinition(skillId);
            if (skill != null) {
                try {
                    // 从缓存获取或转换
                    Example<SkillInfoDO> infoExample = new Example<>();
                    infoExample.createCriteria()
                            .andEqualTo(SkillInfoDO::getSkillId, skillId)
                            .andEqualTo(SkillInfoDO::getIsDelete, false);
                    SkillInfoDO skillInfo = skillInfoMapper.selectOneByExample(infoExample).orElse(null);
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
}
