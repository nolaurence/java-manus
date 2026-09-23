package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.nio.file.Path;
import java.util.Objects;

/**
 * An immutable, filesystem-backed Copilot skill descriptor.
 *
 * <p>The Copilot SDK exposes a skill's frontmatter separately from the
 * markdown instructions.  This descriptor keeps that metadata together with
 * the source locations needed to resolve relative references in the body.</p>
 */
public final class CopilotSkillDescriptor {

    private final String name;
    private final String description;
    /** The configured directory that contains the skill directory. */
    private final Path root;
    /** The canonical path of the skill's {@code SKILL.md} file. */
    private final Path path;
    /** Markdown instructions after the optional YAML frontmatter. */
    private final String body;

    public CopilotSkillDescriptor(String name,
                                  String description,
                                  Path root,
                                  Path path,
                                  String body) {
        this.name = requireText(name, "name");
        this.description = description == null ? "" : description;
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.body = body == null ? "" : body;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String description() {
        return description;
    }

    public String getDescription() {
        return description;
    }

    public Path root() {
        return root;
    }

    public Path getRoot() {
        return root;
    }

    public Path path() {
        return path;
    }

    public Path getPath() {
        return path;
    }

    public String body() {
        return body;
    }

    public String getBody() {
        return body;
    }

    /**
     * Alias for {@link #path()} for callers that need the source file name
     * explicitly.
     */
    public Path skillFile() {
        return path;
    }

    public Path getSkillFile() {
        return path;
    }

    /**
     * Returns the directory containing {@code SKILL.md}.
     */
    public Path skillDirectory() {
        return path.getParent();
    }

    public Path getSkillDirectory() {
        return skillDirectory();
    }

    /**
     * Alias used by some SDK integrations where markdown is called content.
     */
    public String content() {
        return body;
    }

    public String getContent() {
        return body;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CopilotSkillDescriptor)) {
            return false;
        }
        CopilotSkillDescriptor that = (CopilotSkillDescriptor) other;
        return name.equals(that.name)
                && description.equals(that.description)
                && root.equals(that.root)
                && path.equals(that.path)
                && body.equals(that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, root, path, body);
    }

    @Override
    public String toString() {
        return "CopilotSkillDescriptor{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", root=" + root +
                ", path=" + path +
                ", bodyLength=" + body.length() +
                '}';
    }
}
