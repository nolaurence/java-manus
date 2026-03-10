package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.Map;

/**
 * Skill执行结果
 *
 * @author nolaurence
 */
@Data
public class SkillExecutionResult {

    private String skillId;

    private String status;

    private String output;

    private String error;

    private Long durationMs;

    private Map<String, Object> metadata;
}
