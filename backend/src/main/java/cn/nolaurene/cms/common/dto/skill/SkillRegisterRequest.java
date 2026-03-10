package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Skill注册请求
 *
 * @author nolaurence
 */
@Data
public class SkillRegisterRequest {

    private String name;

    private String version;

    private String author;

    private String description;

    private String category;

    private List<TriggerConfig> triggers;

    private List<ToolDefinition> tools;

    private RequiresConfig requires;

    private List<String> osSupport;

    private Integer priority;

    private Long userId;

    /**
     * 文档列表
     */
    private List<SkillDocumentRequest> documents;
}
