package cn.nolaurene.cms.service.sandbox.backend.skill;

import cn.nolaurene.cms.common.sandbox.backend.skill.Skill;
import cn.nolaurene.cms.common.sandbox.backend.skill.SkillManifest;
import cn.nolaurene.cms.dal.entity.AgentSkillDO;
import cn.nolaurene.cms.dal.entity.SkillDO;
import cn.nolaurene.cms.dal.mapper.AgentSkillMapper;
import cn.nolaurene.cms.dal.mapper.SkillMapper;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill management service.
 * Handles skill installation, enabling/disabling, and loading.
 */
@Slf4j
@Service
public class SkillService {

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private AgentSkillMapper agentSkillMapper;

    private static final String SKILLS_BASE_DIR = System.getProperty("user.home") + "/.java-manus/skills";

    /**
     * List all skills for a user.
     */
    public List<Skill> listSkills(String userId) {
        SkillDO param = new SkillDO();
        param.setUserId(userId);
        List<SkillDO> skillDOs = skillMapper.selectList(param);
        return skillDOs.stream().map(this::convertToSkill).collect(Collectors.toList());
    }

    /**
     * Install a skill from a zip file upload.
     */
    public Skill installSkill(String userId, String fileName, String contentBase64) throws IOException {
        if (!fileName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only zip skill packages are supported");
        }

        String skillId = slugify(fileName.replaceAll("(?i)\\.zip$", "")) + "-" + System.currentTimeMillis();
        Path userSkillsDir = Paths.get(SKILLS_BASE_DIR, userId);
        Path skillDir = userSkillsDir.resolve(skillId);
        Path extractDir = skillDir.resolve("source");

        // Clean up if exists
        deleteDirectory(skillDir.toFile());
        Files.createDirectories(extractDir);

        // Decode and extract zip
        byte[] zipBytes = Base64.decodeBase64(contentBase64);
        Path zipPath = userSkillsDir.resolve(skillId + ".zip");
        Files.write(zipPath, zipBytes);
        extractZip(zipPath.toFile(), extractDir.toFile());
        Files.deleteIfExists(zipPath);

        // Resolve skill root (find SKILL.md)
        File skillRoot = findSkillRoot(extractDir.toFile());
        if (skillRoot == null) {
            throw new IllegalArgumentException("Skill package must contain a SKILL.md file");
        }

        // Parse metadata
        SkillManifest manifest = readSkillMetadata(skillRoot);

        // Delete old skill if same skillId exists
        SkillDO existingParam = new SkillDO();
        existingParam.setSkillId(skillId);
        existingParam.setUserId(userId);
        List<SkillDO> existing = skillMapper.selectList(existingParam);
        for (SkillDO e : existing) {
            skillMapper.deleteById(e.getId());
        }

        // Save to DB
        SkillDO skillDO = new SkillDO();
        skillDO.setSkillId(skillId);
        skillDO.setUserId(userId);
        skillDO.setName(manifest.getName() != null ? manifest.getName() : fileName.replaceAll("(?i)\\.zip$", ""));
        skillDO.setDescription(manifest.getDescription());
        skillDO.setVersion(manifest.getVersion());
        skillDO.setTags(manifest.getTags() != null ? String.join(",", manifest.getTags()) : null);
        skillDO.setEnabled(true);
        skillDO.setSourceDir(skillRoot.getAbsolutePath());
        skillDO.setInstalledAt(new Date());
        skillDO.setUpdatedAt(new Date());
        skillMapper.insert(skillDO);

        return convertToSkill(skillDO);
    }

    /**
     * Toggle skill enabled status.
     */
    public Skill toggleSkill(String userId, String skillId, boolean enabled) {
        SkillDO param = new SkillDO();
        param.setSkillId(skillId);
        param.setUserId(userId);
        List<SkillDO> skillDOs = skillMapper.selectList(param);
        if (skillDOs.isEmpty()) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }
        SkillDO skillDO = skillDOs.get(0);
        skillDO.setEnabled(enabled);
        skillDO.setUpdatedAt(new Date());
        skillMapper.updateById(skillDO);
        return convertToSkill(skillDO);
    }

    /**
     * Get enabled skills for an agent.
     */
    public List<Skill> getAgentSkills(String agentId) {
        AgentSkillDO param = new AgentSkillDO();
        param.setAgentId(agentId);
        param.setEnabled(true);
        List<AgentSkillDO> agentSkills = agentSkillMapper.selectList(param);
        
        List<Skill> skills = new ArrayList<>();
        for (AgentSkillDO as : agentSkills) {
            SkillDO skillParam = new SkillDO();
            skillParam.setSkillId(as.getSkillId());
            List<SkillDO> skillDOs = skillMapper.selectList(skillParam);
            if (!skillDOs.isEmpty() && Boolean.TRUE.equals(skillDOs.get(0).getEnabled())) {
                skills.add(convertToSkill(skillDOs.get(0)));
            }
        }
        return skills;
    }

    /**
     * Associate skills with an agent.
     */
    public void setAgentSkills(String agentId, List<String> skillIds) {
        // Remove existing associations
        AgentSkillDO param = new AgentSkillDO();
        param.setAgentId(agentId);
        List<AgentSkillDO> existing = agentSkillMapper.selectList(param);
        for (AgentSkillDO e : existing) {
            agentSkillMapper.deleteById(e.getId());
        }

        // Add new associations
        for (String skillId : skillIds) {
            AgentSkillDO agentSkill = new AgentSkillDO();
            agentSkill.setAgentId(agentId);
            agentSkill.setSkillId(skillId);
            agentSkill.setEnabled(true);
            agentSkill.setGmtCreate(new Date());
            agentSkill.setGmtModified(new Date());
            agentSkillMapper.insert(agentSkill);
        }
    }

    /**
     * Load tool specifications from a skill directory.
     */
    public List<ToolSpecification> loadSkillTools(Skill skill) {
        if (skill.getSourceDir() == null) {
            return Collections.emptyList();
        }
        File toolsDir = new File(skill.getSourceDir(), "tools");
        if (!toolsDir.exists() || !toolsDir.isDirectory()) {
            return Collections.emptyList();
        }

        File toolsJson = new File(toolsDir, "tools.json");
        if (!toolsJson.exists()) {
            return Collections.emptyList();
        }

        try {
            String content = new String(Files.readAllBytes(toolsJson.toPath()));
            List<ToolSpecification> specs = parseToolSpecifications(content);
            return specs;
        } catch (IOException e) {
            log.warn("[SkillService] Failed to load tools for skill {}: {}", skill.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private Skill convertToSkill(SkillDO skillDO) {
        Skill skill = new Skill();
        skill.setId(skillDO.getSkillId());
        skill.setName(skillDO.getName());
        skill.setDescription(skillDO.getDescription());
        skill.setVersion(skillDO.getVersion());
        skill.setTags(skillDO.getTags() != null ? Arrays.asList(skillDO.getTags().split(",")) : Collections.emptyList());
        skill.setEnabled(Boolean.TRUE.equals(skillDO.getEnabled()));
        skill.setSourceDir(skillDO.getSourceDir());
        skill.setInstalledAt(skillDO.getInstalledAt() != null ? skillDO.getInstalledAt().toString() : null);
        skill.setUpdatedAt(skillDO.getUpdatedAt() != null ? skillDO.getUpdatedAt().toString() : null);
        return skill;
    }

    private File findSkillRoot(File dir) {
        File skillMd = new File(dir, "SKILL.md");
        if (skillMd.exists()) {
            return dir;
        }
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                File found = findSkillRoot(subdir);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private SkillManifest readSkillMetadata(File skillRoot) throws IOException {
        File skillMd = new File(skillRoot, "SKILL.md");
        String content = new String(Files.readAllBytes(skillMd.toPath()));
        
        SkillManifest manifest = new SkillManifest();
        
        // Parse front matter
        String frontMatter = content.replaceAll("(?s)^---\\n(.*?)\\n---.*", "$1");
        if (!frontMatter.equals(content)) {
            manifest.setName(readYamlValue(frontMatter, "name"));
            manifest.setDescription(readYamlValue(frontMatter, "description"));
            manifest.setVersion(readYamlValue(frontMatter, "version"));
            String tagsStr = readYamlValue(frontMatter, "tags");
            if (tagsStr != null) {
                manifest.setTags(Arrays.asList(tagsStr.split(",\\s*")));
            }
        }
        
        // Fallback: use first heading as name
        if (manifest.getName() == null) {
            String firstLine = content.lines()
                    .filter(line -> line.trim().startsWith("#"))
                    .findFirst()
                    .orElse("Unnamed Skill");
            manifest.setName(firstLine.replaceAll("^#+\\s*", "").trim());
        }
        
        // Fallback: use first non-empty non-front-matter line as description
        if (manifest.getDescription() == null) {
            manifest.setDescription(content.lines()
                    .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("---") && !line.trim().startsWith("#"))
                    .findFirst()
                    .orElse(null));
        }
        
        return manifest;
    }

    private String readYamlValue(String source, String key) {
        String pattern = "^" + key + ":[\\s]*['\"]?(.+?)['\"]?[\\s]*$";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.MULTILINE).matcher(source);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String slugify(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "")
                .substring(0, Math.min(value.length(), 80));
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    private List<ToolSpecification> parseToolSpecifications(String json) {
        List<ToolSpecification> specs = new ArrayList<>();
        try {
            List<Map<String, Object>> tools = JSON.parseArray(json, Map.class);
            for (Map<String, Object> tool : tools) {
                String name = (String) tool.get("name");
                String description = (String) tool.get("description");
                if (name != null && description != null) {
                    specs.add(ToolSpecification.builder()
                            .name(name)
                            .description(description)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[SkillService] Failed to parse tools.json: {}", e.getMessage());
        }
        return specs;
    }
}
