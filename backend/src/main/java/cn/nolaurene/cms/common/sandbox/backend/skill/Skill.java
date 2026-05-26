package cn.nolaurene.cms.common.sandbox.backend.skill;

import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.Data;

import java.util.List;

/**
 * Skill domain model.
 * A skill is a named collection of tools + metadata that can be enabled/disabled per user.
 */
@Data
public class Skill {
    private String id;
    private String name;
    private String description;
    private String version;
    private List<String> tags;
    private boolean enabled;
    private String sourceDir;
    private String installedAt;
    private String updatedAt;

    /**
     * Tool specifications provided by this skill.
     */
    private List<ToolSpecification> toolSpecifications;
}
