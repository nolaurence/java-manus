package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.common.dto.skill.*;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.dal.entity.SkillDocumentDO;
import cn.nolaurene.cms.dal.entity.UserSkillStatusDO;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillExecutionEngine;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import cn.nolaurene.cms.service.UserLoginService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

/**
 * Skill管理控制器
 *
 * @author nolaurence
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
@Tag(name = "Skill管理", description = "Skill注册、查询、执行相关接口")
public class SkillController {

    @Resource
    private SkillManagementService skillManagementService;

    @Resource
    private SkillExecutionEngine skillExecutionEngine;

    @Resource
    private UserLoginService userLoginService;

    /**
     * 注册新Skill
     */
    @PostMapping
    @Operation(summary = "注册新Skill")
    public BaseWebResult<String> registerSkill(@RequestBody SkillRegisterRequest request) {
        String skillId = skillManagementService.registerSkill(request);
        return BaseWebResult.success(skillId);
    }

    /**
     * 从SKILL.md内容注册Skill
     */
    @PostMapping("/from-md")
    @Operation(summary = "从SKILL.md内容注册Skill")
    public BaseWebResult<String> registerFromSkillMd(
            @RequestBody String skillMdContent,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String skillId = skillManagementService.registerFromSkillMd(skillMdContent, userId);
        return BaseWebResult.success(skillId);
    }

    /**
     * 更新Skill
     */
    @PutMapping("/{skillId}")
    @Operation(summary = "更新Skill")
    public BaseWebResult<Void> updateSkill(
            @PathVariable String skillId,
            @RequestBody SkillUpdateRequest request) {
        skillManagementService.updateSkill(skillId, request);
        return BaseWebResult.success(null);
    }

    /**
     * 删除Skill
     */
    @DeleteMapping("/{skillId}")
    @Operation(summary = "删除Skill")
    public BaseWebResult<Void> deleteSkill(@PathVariable String skillId) {
        skillManagementService.deleteSkill(skillId);
        return BaseWebResult.success(null);
    }

    /**
     * 获取Skill详情
     */
    @GetMapping("/{skillId}")
    @Operation(summary = "获取Skill详情")
    public BaseWebResult<SkillDefinitionDTO> getSkill(@PathVariable String skillId) {
        SkillDefinitionDTO skill = skillManagementService.getSkill(skillId);
        if (skill == null) {
            return BaseWebResult.fail("Skill not found: " + skillId);
        }
        return BaseWebResult.success(skill);
    }

    /**
     * 列出所有Skill
     */
    @GetMapping
    @Operation(summary = "列出所有Skill")
    public BaseWebResult<List<SkillDefinitionDTO>> listSkills(
            @Parameter(description = "分类") @RequestParam(required = false) String category,
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId) {
        List<SkillDefinitionDTO> skills = skillManagementService.listSkills(category, userId);
        return BaseWebResult.success(skills);
    }

    /**
     * 执行Skill
     */
    @PostMapping("/{skillId}/execute")
    @Operation(summary = "执行Skill")
    public BaseWebResult<SkillExecutionResult> executeSkill(
            @PathVariable String skillId,
            @RequestBody SkillExecutionRequest request) {
        request.setSkillId(skillId);
        SkillExecutionResult result = skillExecutionEngine.execute(request);
        return BaseWebResult.success(result);
    }

    /**
     * 匹配Skill
     * 根据用户输入匹配可能适用的Skill
     */
    @PostMapping("/match")
    @Operation(summary = "匹配Skill")
    public BaseWebResult<List<SkillDefinitionDTO>> matchSkills(@RequestBody String input) {
        List<SkillDefinitionDTO> skills = skillExecutionEngine.matchSkills(input);
        return BaseWebResult.success(skills);
    }

    /**
     * 添加Skill文档
     */
    @PostMapping("/{skillId}/documents")
    @Operation(summary = "添加Skill文档")
    public BaseWebResult<Void> addDocument(
            @PathVariable String skillId,
            @RequestBody SkillDocumentRequest request) {
        skillManagementService.addDocument(skillId, request);
        return BaseWebResult.success(null);
    }

    /**
     * 获取Skill文档
     */
    @GetMapping("/{skillId}/documents")
    @Operation(summary = "获取Skill文档")
    public BaseWebResult<List<SkillDocumentDO>> getDocuments(@PathVariable String skillId) {
        List<SkillDocumentDO> documents = skillManagementService.getDocuments(skillId);
        return BaseWebResult.success(documents);
    }

    /**
     * 获取指定类型的Skill文档
     */
    @GetMapping("/{skillId}/documents/{docType}")
    @Operation(summary = "获取指定类型的Skill文档")
    public BaseWebResult<List<SkillDocumentDO>> getDocumentsByType(
            @PathVariable String skillId,
            @PathVariable String docType) {
        List<SkillDocumentDO> documents = skillManagementService.getDocumentsByType(skillId, docType);
        return BaseWebResult.success(documents);
    }

    /**
     * 启用Skill
     */
    @PostMapping("/{skillId}/enable")
    @Operation(summary = "启用Skill")
    public BaseWebResult<Void> enableSkill(@PathVariable String skillId) {
        skillManagementService.enableSkill(skillId);
        return BaseWebResult.success(null);
    }

    /**
     * 禁用Skill
     */
    @PostMapping("/{skillId}/disable")
    @Operation(summary = "禁用Skill")
    public BaseWebResult<Void> disableSkill(@PathVariable String skillId) {
        skillManagementService.disableSkill(skillId);
        return BaseWebResult.success(null);
    }

    /**
     * 刷新Skill缓存
     */
    @PostMapping("/cache/refresh")
    @Operation(summary = "刷新Skill缓存")
    public BaseWebResult<Void> refreshCache(
            @Parameter(description = "Skill ID，不传则刷新全部") @RequestParam(required = false) String skillId) {
        if (skillId != null) {
            skillExecutionEngine.refreshCache(skillId);
        } else {
            skillExecutionEngine.refreshCache();
        }
        return BaseWebResult.success(null);
    }

    /**
     * 预热缓存
     */
    @PostMapping("/cache/warmup")
    @Operation(summary = "预热Skill缓存")
    public BaseWebResult<Void> warmUpCache() {
        skillExecutionEngine.warmUpCache();
        return BaseWebResult.success(null);
    }

    /**
     * 检查Skill是否存在
     */
    @GetMapping("/{skillId}/exists")
    @Operation(summary = "检查Skill是否存在")
    public BaseWebResult<Boolean> exists(@PathVariable String skillId) {
        boolean exists = skillManagementService.exists(skillId);
        return BaseWebResult.success(exists);
    }

    // ==================== Zip文件导入 ====================

    /**
     * 从Zip文件导入Skill
     */
    @PostMapping(value = "/import-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "从Zip文件导入Skill")
    public BaseWebResult<String> importSkillFromZip(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        try {
            if (file.isEmpty()) {
                return BaseWebResult.fail("Zip file is empty");
            }
            String skillId = skillManagementService.importSkillFromZip(file.getBytes(), userId);
            return BaseWebResult.success(skillId);
        } catch (IOException e) {
            log.error("Failed to import skill from zip", e);
            return BaseWebResult.fail("Failed to import skill: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return BaseWebResult.fail("Invalid skill package: " + e.getMessage());
        }
    }

    // ==================== 用户Skill状态管理 ====================

    /**
     * 获取用户启用的Skill列表
     */
    @GetMapping("/user/enabled")
    @Operation(summary = "获取用户启用的Skill列表")
    public BaseWebResult<List<String>> getEnabledSkillsForUser(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            HttpServletRequest httpServletRequest) {
        long finalUserId;
        if (userId == null) {
            finalUserId = userLoginService.getCurrentUserInfo(httpServletRequest).getUserid();
        } else {
            finalUserId = userId;
        }
        List<String> skillIds = skillManagementService.getEnabledSkillIdsForUser(finalUserId);
        return BaseWebResult.success(skillIds);
    }

    /**
     * 为用户启用Skill
     */
    @PostMapping("/{skillId}/enable-for-user")
    @Operation(summary = "为用户启用Skill")
    public BaseWebResult<Void> enableSkillForUser(
            @PathVariable String skillId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            HttpServletRequest httpServletRequest) {
        long finalUserId;
        if (userId == null) {
            finalUserId = userLoginService.getCurrentUserInfo(httpServletRequest).getUserid();
        } else {
            finalUserId = userId;
        }
        skillManagementService.enableSkillForUser(finalUserId, skillId);
        return BaseWebResult.success(null);
    }

    /**
     * 为用户禁用Skill
     */
    @PostMapping("/{skillId}/disable-for-user")
    @Operation(summary = "为用户禁用Skill")
    public BaseWebResult<Void> disableSkillForUser(
            @PathVariable String skillId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            HttpServletRequest httpServletRequest) {
        long finalUserId;
        if (userId == null) {
            finalUserId = userLoginService.getCurrentUserInfo(httpServletRequest).getUserid();
        } else {
            finalUserId = userId;
        }
        skillManagementService.disableSkillForUser(finalUserId, skillId);
        return BaseWebResult.success(null);
    }

    /**
     * 初始化用户Skill状态
     * 为新用户启用所有现有Skill
     */
    @PostMapping("/user/initialize")
    @Operation(summary = "初始化用户Skill状态")
    public BaseWebResult<Void> initializeUserSkillStatus(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            HttpServletRequest httpServletRequest) {
        long finalUserId;
        if (userId == null) {
            finalUserId = userLoginService.getCurrentUserInfo(httpServletRequest).getUserid();
        } else {
            finalUserId = userId;
        }
        skillManagementService.initializeUserSkillStatus(finalUserId);
        return BaseWebResult.success(null);
    }
}
