package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.common.sandbox.backend.skill.Skill;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Skill management REST API.
 * Compatible with pi-cloud-agent skill endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    @Resource
    private SkillService skillService;

    /**
     * List all skills for current user.
     */
    @GetMapping("/{userId}")
    public Response<List<Skill>> listSkills(@PathVariable("userId") String userId) {
        List<Skill> skills = skillService.listSkills(userId);
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
            Skill skill = skillService.installSkill(userId, request.getFileName(), request.getContentBase64());
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
        Skill skill = skillService.toggleSkill(userId, skillId, request.isEnabled());
        return Response.success(skill);
    }

    /**
     * Get skills for an agent.
     */
    @GetMapping("/agent/{agentId}")
    public Response<List<Skill>> getAgentSkills(@PathVariable("agentId") String agentId) {
        List<Skill> skills = skillService.getAgentSkills(agentId);
        return Response.success(skills);
    }

    /**
     * Set skills for an agent.
     */
    @PostMapping("/agent/{agentId}")
    public Response<Void> setAgentSkills(
            @PathVariable("agentId") String agentId,
            @RequestBody SetAgentSkillsRequest request) {
        skillService.setAgentSkills(agentId, request.getSkillIds());
        return Response.success(null);
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
