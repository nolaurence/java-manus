package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Skill更新请求
 *
 * @author nolaurence
 */
@Data
public class SkillUpdateRequest {

    private String description;

    private String category;

    private List<TriggerConfig> triggers;

    private List<ToolDefinition> tools;

    private RequiresConfig requires;

    private List<String> osSupport;

    private Integer priority;

    private Integer status;
}
