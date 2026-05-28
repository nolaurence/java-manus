package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.common.dto.skill.SkillDefinitionDTO;
import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.common.sandbox.backend.skill.Skill;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill management REST API.
 * Compatible with pi-cloud-agent skill endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    @Resource
    private SkillManagementService skillManagementService;

    /**
     * List all skills for current user.
     */
    @GetMapping("/{userId}")
    public Response<List<Skill>> listSkills(@PathVariable("userId") String userId) {
        Long parsedUserId = parseUserId(userId);
        List<Skill> skills = skillManagementService.listAvailableSkillsForUser(parsedUserId).stream()
                .map(this::convertToSkill)
                .collect(Collectors.toList());
        return Response.success(skills);
    }

    /**
     * Install a skill from a zip file.
     */
    @PutMapping("/{userId}/install")
    public Response<Skill> installSkill(
            @PathVariable("userId") String userId,
            @RequestBody InstallSkillRequest request) {
        try {
            Long parsedUserId = parseUserId(userId);
            if (parsedUserId == null) {
                return Response.error("Invalid userId", null);
            }
            byte[] zipData = Base64.getDecoder().decode(request.getContentBase64());
            String skillId = skillManagementService.importSkillFromZip(zipData, parsedUserId);
            SkillDefinitionDTO skillDefinition = skillManagementService.getSkill(skillId);
            Skill skill = convertToSkill(skillDefinition);
            return Response.success(skill);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage(), null);
        } catch (IOException e) {
            log.error("[SkillController] Failed to install skill: {}", e.getMessage());
            return Response.error("Failed to install skill: " + e.getMessage(), null);
        }
    }

    /**
     * Toggle skill enabled status.
     */
    @PutMapping("/{userId}/{skillId}/enabled")
    public Response<Skill> setEnabled(
            @PathVariable("userId") String userId,
            @PathVariable("skillId") String skillId,
            @RequestBody ToggleSkillRequest request) {
        Long parsedUserId = parseUserId(userId);
        if (parsedUserId == null) {
            return Response.error("Invalid userId", null);
        }
        if (request.isEnabled()) {
            skillManagementService.enableSkillForUser(parsedUserId, skillId);
        } else {
            skillManagementService.disableSkillForUser(parsedUserId, skillId);
        }

        SkillDefinitionDTO skillDefinition = skillManagementService.getSkill(skillId);
        Skill skill = convertToSkill(skillDefinition);
        if (skill != null) {
            skill.setEnabled(request.isEnabled());
        }
        return Response.success(skill);
    }

    /**
     * Get skills for an agent.
     */
    @GetMapping("/agent/{agentId}")
    public Response<List<Skill>> getAgentSkills(@PathVariable("agentId") String agentId) {
        return Response.success(List.of());
    }

    /**
     * Set skills for an agent.
     */
    @PostMapping("/agent/{agentId}")
    public Response<Void> setAgentSkills(
            @PathVariable("agentId") String agentId,
            @RequestBody SetAgentSkillsRequest request) {
        return Response.success(null);
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            log.warn("[SkillController] Invalid userId: {}", userId);
            return null;
        }
    }

    private Skill convertToSkill(SkillDefinitionDTO dto) {
        if (dto == null) {
            return null;
        }
        Skill skill = new Skill();
        skill.setId(dto.getSkillId());
        skill.setName(dto.getName());
        skill.setDescription(dto.getDescription());
        skill.setVersion(dto.getVersion());
        skill.setEnabled(dto.getStatus() == 1);
        skill.setTags(List.of());
        return skill;
    }

    // Request DTOs
    public static class InstallSkillRequest {
        private String fileName;
        private String contentBase64;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContentBase64() { return contentBase64; }
        public void setContentBase64(String contentBase64) { this.contentBase64 = contentBase64; }
    }

    public static class ToggleSkillRequest {
        private boolean enabled;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class SetAgentSkillsRequest {
        private List<String> skillIds;

        public List<String> getSkillIds() { return skillIds; }
        public void setSkillIds(List<String> skillIds) { this.skillIds = skillIds; }
    }
}
