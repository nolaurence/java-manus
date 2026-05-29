package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.Map;

/**
 * Skill执行请求
 *
 * @author nolaurence
 */
@Data
public class SkillExecutionRequest {

    private String skillId;

    private String sessionId;

    private Map<String, Object> params;

    private String workingDir;

    /**
     * 执行用户ID（用于权限检查）
     */
    private Long userId;
}
