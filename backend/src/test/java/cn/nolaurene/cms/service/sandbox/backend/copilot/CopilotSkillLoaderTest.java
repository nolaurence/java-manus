package cn.nolaurene.cms.service.sandbox.backend.copilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSkillLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversImmediateChildrenAndParsesFrontmatterWithoutLosingBody() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        Path review = Files.createDirectory(root.resolve("review"));
        Files.writeString(review.resolve("SKILL.md"), """
                ---
                name: "code-review"
                description: 'Review: Java code'
                ---
                # Review checklist
                Keep this line: it contains a colon.
                ---
                Keep this separator in the body.
                """);

        Path nested = Files.createDirectories(root.resolve("group").resolve("nested"));
        Files.writeString(nested.resolve("SKILL.md"), "name: nested\n---\nnot immediate");

        List<CopilotSkillDescriptor> skills = new CopilotSkillLoader(root).load();

        assertEquals(1, skills.size());
        CopilotSkillDescriptor descriptor = skills.get(0);
        assertEquals("code-review", descriptor.name());
        assertEquals("Review: Java code", descriptor.description());
        assertEquals(root.toRealPath(), descriptor.root());
        assertEquals(review.resolve("SKILL.md").toRealPath(), descriptor.path());
        assertTrue(descriptor.body().contains("Keep this line: it contains a colon."));
        assertTrue(descriptor.body().contains("---\nKeep this separator"));
    }

    @Test
    void fallsBackToDirectoryNameAndHonoursDisablesAndRootPriority() throws IOException {
        Path first = Files.createDirectories(tempDir.resolve("first"));
        Path second = Files.createDirectories(tempDir.resolve("second"));
        Files.createDirectories(first.resolve("same"));
        Files.writeString(first.resolve("same/SKILL.md"), "first");
        Files.createDirectories(first.resolve("disabled"));
        Files.writeString(first.resolve("disabled/SKILL.md"), "disabled");
        Files.createDirectories(second.resolve("same"));
        Files.writeString(second.resolve("same/SKILL.md"), "second");

        List<CopilotSkillDescriptor> skills = new CopilotSkillLoader(
                List.of(first, second), Set.of("disabled")).load();

        assertEquals(List.of("same"), skills.stream()
                .map(CopilotSkillDescriptor::name)
                .collect(Collectors.toList()));
        assertEquals(first.resolve("same/SKILL.md").toRealPath(), skills.get(0).path());
        assertFalse(skills.stream().anyMatch(skill -> skill.name().equals("disabled")));
    }

    @Test
    void rejectsSkillFilesThatEscapeRootThroughSymlinks() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("skills"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path outsideFile = Files.writeString(outside.resolve("SKILL.md"), "name: escaped\n---\nunsafe");
        Path skill = Files.createDirectories(root.resolve("escaped"));

        try {
            Files.createSymbolicLink(skill.resolve("SKILL.md"), outsideFile);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
            // Symlinks are unavailable on some CI filesystems; the other
            // discovery tests still cover the loader contract there.
            return;
        }

        assertTrue(new CopilotSkillLoader(root).load().isEmpty());
    }

    @Test
    void exposesTheSameFrontmatterParserUsedByRuntimeDiscovery() {
        String source = "\ufeff---\r\n"
                + "name: demo\r\n"
                + "description: \"A description: with a colon\"\r\n"
                + "---\r\n"
                + "# Instructions\r\n";

        assertEquals(Map.of("name", "demo", "description", "A description: with a colon"),
                CopilotSkillLoader.parseFrontmatter(source));
        assertEquals("# Instructions\n", CopilotSkillLoader.extractBody(source));
    }
}
