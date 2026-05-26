package cn.nolaurene.cms.common.sandbox.backend.skill;

import lombok.Data;

import java.util.List;

/**
 * Parsed SKILL.md manifest.
 */
@Data
public class SkillManifest {
    private String id;
    private String name;
    private String description;
    private String version = "1.0.0";
    private List<String> tags;
    private boolean enabled = true;
}
