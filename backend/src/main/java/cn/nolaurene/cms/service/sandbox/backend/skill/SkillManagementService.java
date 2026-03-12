package cn.nolaurene.cms.service.sandbox.backend.skill;

import cn.nolaurene.cms.common.dto.skill.*;
import cn.nolaurene.cms.dal.entity.SkillDocumentDO;
import cn.nolaurene.cms.dal.entity.SkillInfoDO;
import cn.nolaurene.cms.dal.entity.UserSkillStatusDO;
import cn.nolaurene.cms.dal.mapper.SkillDocumentMapper;
import cn.nolaurene.cms.dal.mapper.SkillInfoMapper;
import cn.nolaurene.cms.dal.mapper.UserSkillStatusMapper;
import cn.nolaurene.cms.exception.skill.SkillAlreadyExistsException;
import cn.nolaurene.cms.exception.skill.SkillNotFoundException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.mybatis.mapper.example.Example;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill管理服务
 * 负责Skill的注册、更新、删除和查询
 *
 * @author nolaurence
 */
@Slf4j
@Service
public class SkillManagementService {

    private final SkillInfoMapper skillInfoMapper;
    private final SkillDocumentMapper skillDocumentMapper;
    private final UserSkillStatusMapper userSkillStatusMapper;
    private final SkillExecutionEngine executionEngine;
    private final SkillFileStorageService fileStorageService;

    public SkillManagementService(SkillInfoMapper skillInfoMapper,
                                  SkillDocumentMapper skillDocumentMapper,
                                  UserSkillStatusMapper userSkillStatusMapper,
                                  SkillExecutionEngine executionEngine,
                                  SkillFileStorageService fileStorageService) {
        this.skillInfoMapper = skillInfoMapper;
        this.skillDocumentMapper = skillDocumentMapper;
        this.userSkillStatusMapper = userSkillStatusMapper;
        this.executionEngine = executionEngine;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 注册新Skill
     *
     * @param request Skill注册请求
     * @return Skill ID
     */
    @Transactional
    public String registerSkill(SkillRegisterRequest request) {
        // 生成Skill ID（使用规范化后的name）
        String skillId = generateSkillId(request.getName());

        // 检查是否已存在
        Example<SkillInfoDO> checkExample = new Example<>();
        checkExample.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        SkillInfoDO existing = skillInfoMapper.selectOneByExample(checkExample).orElse(null);
        if (existing != null) {
            throw new SkillAlreadyExistsException("Skill already exists: " + skillId);
        }

        // 创建Skill信息
        SkillInfoDO skillInfo = new SkillInfoDO();
        skillInfo.setSkillId(skillId);
        skillInfo.setName(request.getName());
        skillInfo.setDescription(request.getDescription());
        skillInfo.setVersion(request.getVersion() != null ? request.getVersion() : "1.0.0");
        skillInfo.setLicense(request.getLicense());
        skillInfo.setCompatibility(request.getCompatibility());
        if (request.getMetadata() != null) {
            skillInfo.setMetadata(JSON.toJSONString(request.getMetadata()));
        }
        skillInfo.setAllowedTools(request.getAllowedTools());
        skillInfo.setStatus(1);
        skillInfo.setUserId(request.getUserId());
        skillInfo.setGmtCreate(new Date());
        skillInfo.setGmtModified(new Date());
        skillInfo.setIsDelete(false);

        skillInfoMapper.insert(skillInfo);

        // 保存文档（支持多文档）
        if (request.getDocuments() != null && !request.getDocuments().isEmpty()) {
            int sortOrder = 0;
            for (SkillDocumentRequest docRequest : request.getDocuments()) {
                SkillDocumentDO document = new SkillDocumentDO();
                document.setSkillId(skillId);
                document.setDocType(docRequest.getDocType());
                document.setDocName(docRequest.getDocName());
                document.setContent(docRequest.getContent());
                document.setFilePath(docRequest.getFilePath());
                document.setSortOrder(sortOrder++);
                document.setGmtCreate(new Date());
                document.setGmtModified(new Date());
                document.setIsDelete(false);
                skillDocumentMapper.insert(document);
            }
        }

        log.info("Skill registered: {}", skillId);
        return skillId;
    }

    /**
     * 从SKILL.md内容解析并注册Skill
     *
     * @param skillMdContent SKILL.md文件内容
     * @param userId         用户ID
     * @return Skill ID
     */
    @Transactional
    public String registerFromSkillMd(String skillMdContent, Long userId) {
        // 解析YAML frontmatter
        SkillParseResult parseResult = parseSkillMd(skillMdContent);

        SkillRegisterRequest request = new SkillRegisterRequest();
        request.setName(parseResult.getName());
        request.setDescription(parseResult.getDescription());
        request.setVersion(parseResult.getVersion());
        // author 存储在 metadata 中，符合 Agent Skills 规范
        request.setLicense(parseResult.getLicense());
        request.setCompatibility(parseResult.getCompatibility());
        request.setMetadata(parseResult.getMetadata());
        request.setAllowedTools(parseResult.getAllowedTools());
        request.setUserId(userId);

        // 添加SKILL.md作为文档
        List<SkillDocumentRequest> documents = new ArrayList<>();
        SkillDocumentRequest doc = new SkillDocumentRequest();
        doc.setDocType("SKILL_MD");
        doc.setDocName("SKILL.md");
        doc.setContent(skillMdContent);
        documents.add(doc);
        request.setDocuments(documents);

        return registerSkill(request);
    }

    /**
     * SKILL.md解析结果
     */
    private static class SkillParseResult {
        private String name;
        private String description;
        private String version;
        private String author;
        private String license;
        private String compatibility;
        private Map<String, String> metadata;
        private String allowedTools;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getLicense() { return license; }
        public void setLicense(String license) { this.license = license; }
        public String getCompatibility() { return compatibility; }
        public void setCompatibility(String compatibility) { this.compatibility = compatibility; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
        public String getAllowedTools() { return allowedTools; }
        public void setAllowedTools(String allowedTools) { this.allowedTools = allowedTools; }
    }

    /**
     * 解析SKILL.md文件
     */
    private SkillParseResult parseSkillMd(String content) {
        SkillParseResult result = new SkillParseResult();

        if (content == null || content.isEmpty()) {
            return result;
        }

        // 提取YAML frontmatter
        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                String frontmatter = content.substring(3, endIndex).trim();

                // 解析YAML
                Map<String, Object> yaml = parseYaml(frontmatter);

                result.setName((String) yaml.get("name"));
                result.setDescription((String) yaml.get("description"));
                result.setVersion((String) yaml.getOrDefault("version", "1.0.0"));
                result.setLicense((String) yaml.get("license"));
                result.setCompatibility((String) yaml.get("compatibility"));

                // 解析metadata
                Object metadataObj = yaml.get("metadata");
                Map<String, String> metadata = new HashMap<>();
                if (metadataObj instanceof Map) {
                    ((Map<?, ?>) metadataObj).forEach((k, v) -> metadata.put(String.valueOf(k), String.valueOf(v)));
                }
                result.setMetadata(metadata);

                // 根据 Agent Skills 规范，author 应该在 metadata 中
                // 先从 metadata 中读取 author，如果不存在则使用默认值
                String author = metadata.get("author");
                if (author == null || author.isEmpty()) {
                    author = "anonymous";
                }
                result.setAuthor(author);

                // 解析allowed-tools (YAML中可能是 allowed-tools 或 allowedTools)
                Object allowedToolsObj = yaml.get("allowed-tools");
                if (allowedToolsObj == null) {
                    allowedToolsObj = yaml.get("allowedTools");
                }
                if (allowedToolsObj != null) {
                    result.setAllowedTools(String.valueOf(allowedToolsObj));
                }
            }
        }

        return result;
    }

    /**
     * 简单YAML解析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String yaml) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] lines = yaml.split("\n");

        for (String line : lines) {
            if (StringUtils.isBlank(line)) {
                continue;
            }

            String trimmed = line.trim();

            // 跳过注释
            if (trimmed.startsWith("#")) {
                continue;
            }

            // 解析键值对
            if (trimmed.contains(":")) {
                int colonIndex = trimmed.indexOf(":");
                String key = trimmed.substring(0, colonIndex).trim();
                String value = trimmed.substring(colonIndex + 1).trim();

                if (StringUtils.isNotBlank(value)) {
                    // 解析值
                    result.put(key, parseYamlValue(value));
                }
            }
        }

        return result;
    }

    /**
     * 解析YAML值
     */
    private Object parseYamlValue(String value) {
        // 移除引号
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        // 数组格式 [a, b, c]
        if (value.startsWith("[") && value.endsWith("]")) {
            String inner = value.substring(1, value.length() - 1).trim();
            if (StringUtils.isBlank(inner)) {
                return new ArrayList<>();
            }
            String[] items = inner.split(",\\s*");
            return Arrays.asList(items);
        }

        // 布尔值
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        // 数字
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            // 不是数字，返回字符串
        }

        return value;
    }

    /**
     * 更新Skill
     */
    @Transactional
    public void updateSkill(String skillId, SkillUpdateRequest request) {
        Example<SkillInfoDO> findExample = new Example<>();
        findExample.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        SkillInfoDO skillInfo = skillInfoMapper.selectOneByExample(findExample).orElse(null);
        if (skillInfo == null) {
            throw new SkillNotFoundException("Skill not found: " + skillId);
        }

        SkillInfoDO updateDO = new SkillInfoDO();
        updateDO.setGmtModified(new Date());

        // 注意：不允许修改name，因为name是skillId的一部分
        // 如果需要修改name，应该删除旧skill并创建新skill
        if (request.getName() != null) {
            log.warn("Ignoring name update for skill {}, name modification is not allowed", skillId);
        }
        if (request.getDescription() != null) {
            updateDO.setDescription(request.getDescription());
        }
        if (request.getVersion() != null) {
            updateDO.setVersion(request.getVersion());
        }
        if (request.getLicense() != null) {
            updateDO.setLicense(request.getLicense());
        }
        if (request.getCompatibility() != null) {
            updateDO.setCompatibility(request.getCompatibility());
        }
        if (request.getMetadata() != null) {
            updateDO.setMetadata(JSON.toJSONString(request.getMetadata()));
        }
        if (request.getAllowedTools() != null) {
            updateDO.setAllowedTools(request.getAllowedTools());
        }
        if (request.getStatus() != null) {
            updateDO.setStatus(request.getStatus());
        }

        Example<SkillInfoDO> updateExample = new Example<>();
        updateExample.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        skillInfoMapper.updateByExampleSelective(updateDO, updateExample);

        // 刷新缓存
        executionEngine.refreshCache(skillId);

        log.info("Skill updated: {}", skillId);
    }

    /**
     * 删除Skill
     */
    @Transactional
    public void deleteSkill(String skillId) {
        Example<SkillInfoDO> findExample = new Example<>();
        findExample.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        SkillInfoDO skillInfo = skillInfoMapper.selectOneByExample(findExample).orElse(null);
        if (skillInfo == null) {
            throw new SkillNotFoundException("Skill not found: " + skillId);
        }

        // 软删除 skill_info
        SkillInfoDO skillUpdate = new SkillInfoDO();
        skillUpdate.setIsDelete(true);
        skillUpdate.setGmtModified(new Date());
        Example<SkillInfoDO> skillDeleteExample = new Example<>();
        skillDeleteExample.createCriteria().andEqualTo(SkillInfoDO::getSkillId, skillId);
        skillInfoMapper.updateByExampleSelective(skillUpdate, skillDeleteExample);

        // 软删除 skill_document
        SkillDocumentDO docUpdate = new SkillDocumentDO();
        docUpdate.setIsDelete(true);
        docUpdate.setGmtModified(new Date());
        Example<SkillDocumentDO> docDeleteExample = new Example<>();
        docDeleteExample.createCriteria().andEqualTo(SkillDocumentDO::getSkillId, skillId);
        skillDocumentMapper.updateByExampleSelective(docUpdate, docDeleteExample);

        executionEngine.refreshCache(skillId);

        log.info("Skill deleted: {}", skillId);
    }

    /**
     * 获取Skill详情
     */
    public SkillDefinitionDTO getSkill(String skillId) {
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        SkillInfoDO skillInfo = skillInfoMapper.selectOneByExample(example).orElse(null);
        if (skillInfo == null) {
            return null;
        }

        return convertToDTO(skillInfo);
    }

    /**
     * 转换为DTO
     */
    private SkillDefinitionDTO convertToDTO(SkillInfoDO skillInfo) {
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
            dto.setAuthor(author != null ? author : "SYSTEM_WIDE");
        } else {
            dto.setAuthor("anonymous");
        }
        dto.setAllowedTools(skillInfo.getAllowedTools());
        dto.setUserId(skillInfo.getUserId());
        dto.setStatus(skillInfo.getStatus());

        // 加载文档
        Example<SkillDocumentDO> docExample = new Example<>();
        docExample.createCriteria()
                .andEqualTo(SkillDocumentDO::getSkillId, skillInfo.getSkillId())
                .andEqualTo(SkillDocumentDO::getIsDelete, false);
        docExample.orderBy(SkillDocumentDO::getSortOrder, Example.Order.ASC);
        List<SkillDocumentDO> documents = skillDocumentMapper.selectByExample(docExample);
        dto.setDocuments(documents);

        return dto;
    }

    /**
     * 列出所有Skill
     */
    public List<SkillDefinitionDTO> listSkills(Long userId) {
        List<SkillInfoDO> skills;

        if (userId != null) {
            Example<SkillInfoDO> example = new Example<>();
            example.createCriteria()
                    .andEqualTo(SkillInfoDO::getUserId, userId)
                    .andEqualTo(SkillInfoDO::getIsDelete, false);
            example.orderByDesc(SkillInfoDO::getGmtCreate);
            skills = skillInfoMapper.selectByExample(example);
        } else {
            Example<SkillInfoDO> example = new Example<>();
            example.createCriteria()
                    .andEqualTo(SkillInfoDO::getStatus, 1)
                    .andEqualTo(SkillInfoDO::getIsDelete, false);
            example.orderByDesc(SkillInfoDO::getGmtCreate);
            skills = skillInfoMapper.selectByExample(example);
        }

        List<SkillDefinitionDTO> result = new ArrayList<>();
        for (SkillInfoDO skill : skills) {
            result.add(convertToDTO(skill));
        }
        return result;
    }

    /**
     * 添加Skill文档
     */
    @Transactional
    public void addDocument(String skillId, SkillDocumentRequest request) {
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        SkillInfoDO skillInfo = skillInfoMapper.selectOneByExample(example).orElse(null);
        if (skillInfo == null) {
            throw new SkillNotFoundException("Skill not found: " + skillId);
        }

        SkillDocumentDO document = new SkillDocumentDO();
        document.setSkillId(skillId);
        document.setDocType(request.getDocType());
        document.setDocName(request.getDocName());
        document.setContent(request.getContent());
        document.setFilePath(request.getFilePath());
        document.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        document.setGmtCreate(new Date());
        document.setGmtModified(new Date());
        document.setIsDelete(false);

        skillDocumentMapper.insert(document);

        log.info("Document added to skill {}: {}", skillId, request.getDocName());
    }

    /**
     * 获取Skill文档
     */
    public List<SkillDocumentDO> getDocuments(String skillId) {
        Example<SkillDocumentDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillDocumentDO::getSkillId, skillId)
                .andEqualTo(SkillDocumentDO::getIsDelete, false);
        example.orderBy(SkillDocumentDO::getSortOrder, Example.Order.ASC);
        return skillDocumentMapper.selectByExample(example);
    }

    /**
     * 获取指定类型的Skill文档
     */
    public List<SkillDocumentDO> getDocumentsByType(String skillId, String docType) {
        Example<SkillDocumentDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillDocumentDO::getSkillId, skillId)
                .andEqualTo(SkillDocumentDO::getDocType, docType)
                .andEqualTo(SkillDocumentDO::getIsDelete, false);
        example.orderBy(SkillDocumentDO::getSortOrder, Example.Order.ASC);
        return skillDocumentMapper.selectByExample(example);
    }

    /**
     * Skill名称验证正则：小写字母、数字、连字符，1-64字符，不能以连字符开头或结尾
     */
    private static final java.util.regex.Pattern SKILL_NAME_PATTERN = java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$|^[a-z0-9]$");

    /**
     * 生成Skill ID
     * 直接使用规范化后的 name 作为 skillId
     */
    private String generateSkillId(String name) {
        String normalizedName = normalizeName(name);
        validateSkillName(normalizedName);
        return normalizedName;
    }

    /**
     * 规范化名称：转小写，替换空格和特殊字符为连字符，去除首尾连字符，合并连续连字符
     */
    private String normalizeName(String input) {
        if (StringUtils.isBlank(input)) {
            throw new IllegalArgumentException("Name is required");
        }
        // 转小写
        String normalized = input.toLowerCase();
        // 替换非字母数字字符为连字符
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        // 去除首尾连字符
        normalized = normalized.replaceAll("^-+|-+$", "");
        // 限制长度为64字符
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
            // 去除末尾可能的连字符
            normalized = normalized.replaceAll("-+$", "");
        }
        
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalArgumentException("Name cannot be empty after normalization");
        }
        return normalized;
    }

    /**
     * 验证Skill名称是否符合规范
     * 规范：小写字母、数字、连字符，1-64字符，不能以连字符开头或结尾，不能有连续连字符
     */
    private void validateSkillName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Skill name is required");
        }
        if (name.length() > 64) {
            throw new IllegalArgumentException("Skill name must be 1-64 characters");
        }
        if (!SKILL_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Skill name must contain only lowercase letters, numbers, and hyphens, " +
                "cannot start or end with hyphen: " + name);
        }
        if (name.contains("--")) {
            throw new IllegalArgumentException("Skill name cannot contain consecutive hyphens: " + name);
        }
    }

    /**
     * 检查Skill是否存在
     */
    public boolean exists(String skillId) {
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        return skillInfoMapper.selectOneByExample(example).isPresent();
    }

    /**
     * 启用Skill
     */
    @Transactional
    public void enableSkill(String skillId) {
        SkillInfoDO updateDO = new SkillInfoDO();
        updateDO.setStatus(1);
        updateDO.setGmtModified(new Date());
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        skillInfoMapper.updateByExampleSelective(updateDO, example);
        executionEngine.refreshCache(skillId);
        log.info("Skill enabled: {}", skillId);
    }

    /**
     * 禁用Skill
     */
    @Transactional
    public void disableSkill(String skillId) {
        SkillInfoDO updateDO = new SkillInfoDO();
        updateDO.setStatus(0);
        updateDO.setGmtModified(new Date());
        Example<SkillInfoDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(SkillInfoDO::getSkillId, skillId)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        skillInfoMapper.updateByExampleSelective(updateDO, example);
        executionEngine.refreshCache(skillId);
        log.info("Skill disabled: {}", skillId);
    }

    // ==================== 用户Skill状态管理 ====================

    /**
     * 初始化用户的Skill状态
     * 当新用户注册或新Skill添加时调用，默认启用所有Skill
     */
    @Transactional
    public void initializeUserSkillStatus(Long userId) {
        Example<SkillInfoDO> activeExample = new Example<>();
        activeExample.createCriteria()
                .andEqualTo(SkillInfoDO::getStatus, 1)
                .andEqualTo(SkillInfoDO::getIsDelete, false);
        activeExample.orderByDesc(SkillInfoDO::getGmtCreate);
        List<SkillInfoDO> allSkills = skillInfoMapper.selectByExample(activeExample);

        for (SkillInfoDO skill : allSkills) {
            Example<UserSkillStatusDO> statusExample = new Example<>();
            statusExample.createCriteria()
                    .andEqualTo(UserSkillStatusDO::getUserId, userId)
                    .andEqualTo(UserSkillStatusDO::getSkillId, skill.getSkillId());
            UserSkillStatusDO existing = userSkillStatusMapper.selectOneByExample(statusExample).orElse(null);
            if (existing == null) {
                UserSkillStatusDO status = new UserSkillStatusDO();
                status.setUserId(userId);
                status.setSkillId(skill.getSkillId());
                status.setStatus(1); // 默认启用
                status.setGmtCreate(new Date());
                status.setGmtModified(new Date());
                userSkillStatusMapper.insert(status);
            }
        }
        log.info("Initialized skill status for user: {}", userId);
    }

    /**
     * 为用户启用Skill
     */
    @Transactional
    public void enableSkillForUser(Long userId, String skillId) {
        Example<UserSkillStatusDO> findExample = new Example<>();
        findExample.createCriteria()
                .andEqualTo(UserSkillStatusDO::getUserId, userId)
                .andEqualTo(UserSkillStatusDO::getSkillId, skillId);

        UserSkillStatusDO existing = userSkillStatusMapper.selectOneByExample(findExample).orElse(null);
        if (existing != null) {
            UserSkillStatusDO updateDO = new UserSkillStatusDO();
            updateDO.setStatus(1);
            updateDO.setGmtModified(new Date());
            Example<UserSkillStatusDO> updateExample = new Example<>();
            updateExample.createCriteria()
                    .andEqualTo(UserSkillStatusDO::getUserId, userId)
                    .andEqualTo(UserSkillStatusDO::getSkillId, skillId);
            userSkillStatusMapper.updateByExampleSelective(updateDO, updateExample);
        } else {
            UserSkillStatusDO status = new UserSkillStatusDO();
            status.setUserId(userId);
            status.setSkillId(skillId);
            status.setStatus(1);
            status.setGmtCreate(new Date());
            status.setGmtModified(new Date());

            userSkillStatusMapper.insert(status);
        }
        log.info("Enabled skill {} for user {}", skillId, userId);
    }

    /**
     * 为用户禁用Skill
     */
    @Transactional
    public void disableSkillForUser(Long userId, String skillId) {
        Example<UserSkillStatusDO> findExample = new Example<>();
        findExample.createCriteria()
                .andEqualTo(UserSkillStatusDO::getUserId, userId)
                .andEqualTo(UserSkillStatusDO::getSkillId, skillId)
;
        UserSkillStatusDO existing = userSkillStatusMapper.selectOneByExample(findExample).orElse(null);
        if (existing != null) {
            UserSkillStatusDO updateDO = new UserSkillStatusDO();
            updateDO.setStatus(0);
            updateDO.setGmtModified(new Date());
            Example<UserSkillStatusDO> updateExample = new Example<>();
            updateExample.createCriteria()
                    .andEqualTo(UserSkillStatusDO::getUserId, userId)
                    .andEqualTo(UserSkillStatusDO::getSkillId, skillId);
            userSkillStatusMapper.updateByExampleSelective(updateDO, updateExample);
        } else {
            UserSkillStatusDO status = new UserSkillStatusDO();
            status.setUserId(userId);
            status.setSkillId(skillId);
            status.setStatus(0);
            status.setGmtCreate(new Date());
            status.setGmtModified(new Date());

            userSkillStatusMapper.insert(status);
        }
        log.info("Disabled skill {} for user {}", skillId, userId);
    }

    /**
     * 获取用户启用的Skill列表
     */
    public List<String> getEnabledSkillIdsForUser(Long userId) {
        Example<UserSkillStatusDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(UserSkillStatusDO::getUserId, userId)
                .andEqualTo(UserSkillStatusDO::getStatus, 1)
;
        return userSkillStatusMapper.selectByExample(example).stream()
                .map(UserSkillStatusDO::getSkillId)
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否启用了某个Skill
     */
    public boolean isSkillEnabledForUser(Long userId, String skillId) {
        Example<UserSkillStatusDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(UserSkillStatusDO::getUserId, userId)
                .andEqualTo(UserSkillStatusDO::getSkillId, skillId)
;
        UserSkillStatusDO status = userSkillStatusMapper.selectOneByExample(example).orElse(null);
        return status != null && status.getStatus() != null && status.getStatus() == 1;
    }

    // ==================== Zip文件导入 ====================

    /**
     * 从Zip文件导入Skill
     *
     * @param zipData  zip文件数据
     * @param userId   上传用户ID
     * @return Skill ID
     */
    @Transactional
    public String importSkillFromZip(byte[] zipData, Long userId) throws IOException {
        // 1. 先保存到临时位置
        String tempId = UUID.randomUUID().toString();
        String tempZipPath = fileStorageService.saveUploadedZip("temp-" + tempId, zipData);

        // 2. 解压到临时目录
        String tempExtractPath = fileStorageService.extractSkillZip("temp-" + tempId);

        try {
            // 3. 读取 SKILL.md 内容
            String skillMdContent = fileStorageService.readTempSkillFile(tempExtractPath);
            if (StringUtils.isBlank(skillMdContent)) {
                throw new IllegalArgumentException("SKILL.md not found in zip file");
            }

            // 4. 解析 SKILL.md
            SkillParseResult parseResult = parseSkillMd(skillMdContent);
            if (StringUtils.isBlank(parseResult.getName()) || StringUtils.isBlank(parseResult.getDescription())) {
                throw new IllegalArgumentException("SKILL.md must contain 'name' and 'description' fields");
            }

            String skillId = generateSkillId(parseResult.getName());

            // 5. 检查是否已存在
            if (exists(skillId)) {
                throw new SkillAlreadyExistsException("Skill already exists: " + skillId);
            }

            // 6. 移动到正式目录
            String finalPath = fileStorageService.moveToExtracted(skillId, tempExtractPath);

            // 7. 保存zip文件
            fileStorageService.saveUploadedZip(skillId, zipData);

            // 8. 注册到数据库
            SkillRegisterRequest request = new SkillRegisterRequest();
            request.setName(parseResult.getName());
            request.setDescription(parseResult.getDescription());
            request.setVersion(parseResult.getVersion());
            // author 存储在 metadata 中，符合 Agent Skills 规范
            request.setLicense(parseResult.getLicense());
            request.setCompatibility(parseResult.getCompatibility());
            request.setMetadata(parseResult.getMetadata());
            request.setAllowedTools(parseResult.getAllowedTools());
            request.setUserId(userId);

            // 添加文档
            List<SkillDocumentRequest> documents = new ArrayList<>();

            // SKILL.md
            SkillDocumentRequest skillDoc = new SkillDocumentRequest();
            skillDoc.setDocType("SKILL_MD");
            skillDoc.setDocName("SKILL.md");
            skillDoc.setContent(skillMdContent);
            documents.add(skillDoc);

            // 读取其他文档
            addDocumentIfExists(documents, skillId, "reference.md", "REFERENCE");
            addDocumentIfExists(documents, skillId, "examples.md", "EXAMPLE");
            addDocumentIfExists(documents, skillId, "README.md", "README");

            request.setDocuments(documents);

            registerSkill(request);

            // 9. 为新Skill初始化所有用户的状态（默认启用）
            initializeSkillStatusForAllUsers(skillId);

            log.info("Successfully imported skill {} from zip", skillId);
            return skillId;

        } finally {
            // 清理临时文件
            try {
                fileStorageService.deleteSkillFiles("temp-" + tempId);
            } catch (Exception e) {
                log.warn("Failed to cleanup temp files for: temp-{}", tempId, e);
            }
        }
    }

    /**
     * 如果文档存在，添加到列表
     */
    private void addDocumentIfExists(List<SkillDocumentRequest> documents, String skillId,
                                     String fileName, String docType) {
        String content = fileStorageService.readSkillFile(skillId, fileName);
        if (StringUtils.isNotBlank(content)) {
            SkillDocumentRequest doc = new SkillDocumentRequest();
            doc.setDocType(docType);
            doc.setDocName(fileName);
            doc.setContent(content);
            documents.add(doc);
        }
    }

    /**
     * 为新Skill初始化所有现有用户的状态
     */
    @Transactional
    public void initializeSkillStatusForAllUsers(String skillId) {
        // 这里假设有一个方法可以获取所有用户ID
        // 实际实现中可能需要注入UserService
        // 简化处理：当用户首次获取Skill列表时，再初始化其状态
        log.info("Skill {} registered, user statuses will be initialized on first access", skillId);
    }
}
