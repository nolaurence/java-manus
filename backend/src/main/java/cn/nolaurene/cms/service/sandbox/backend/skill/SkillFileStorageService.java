package cn.nolaurene.cms.service.sandbox.backend.skill;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill文件存储服务
 * 管理Skill文件的存储、读取和解压
 *
 * @author nolaurence
 */
@Slf4j
@Service
public class SkillFileStorageService {

    @Value("${skill.storage.path:/app/skills}")
    private String storagePath;

    private Path basePath;
    private Path uploadedPath;
    private Path extractedPath;
    private Path tempPath;

    @PostConstruct
    public void init() {
        this.basePath = Paths.get(storagePath);
        this.uploadedPath = basePath.resolve("uploaded");
        this.extractedPath = basePath.resolve("extracted");
        this.tempPath = basePath.resolve("temp");

        try {
            Files.createDirectories(uploadedPath);
            Files.createDirectories(extractedPath);
            Files.createDirectories(tempPath);
            log.info("Skill file storage initialized at: {}", basePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create skill storage directories", e);
            throw new RuntimeException("Failed to initialize skill file storage", e);
        }
    }

    /**
     * 保存上传的zip文件
     *
     * @param skillId Skill ID
     * @param zipData zip文件数据
     * @return 保存的文件路径
     */
    public String saveUploadedZip(String skillId, byte[] zipData) throws IOException {
        Path zipPath = uploadedPath.resolve(skillId + ".zip");
        Files.write(zipPath, zipData);
        log.info("Saved uploaded zip for skill {}: {}", skillId, zipPath);
        return zipPath.toString();
    }

    /**
     * 解压Skill zip文件
     *
     * @param skillId Skill ID
     * @return 解压后的目录路径
     */
    public String extractSkillZip(String skillId) throws IOException {
        Path zipPath = uploadedPath.resolve(skillId + ".zip");
        if (!Files.exists(zipPath)) {
            throw new FileNotFoundException("Zip file not found for skill: " + skillId);
        }

        // 创建临时解压目录
        String tempId = UUID.randomUUID().toString();
        Path tempExtractPath = tempPath.resolve(tempId);
        Files.createDirectories(tempExtractPath);

        // 解压文件
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = tempExtractPath.resolve(entry.getName());

                // 安全检查：防止zip slip攻击
                if (!entryPath.normalize().startsWith(tempExtractPath.normalize())) {
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        log.info("Extracted skill {} to temp directory: {}", skillId, tempExtractPath);
        return tempExtractPath.toString();
    }

    /**
     * 解析skillId为路径
     * skillId 直接作为目录名
     * 
     * @param skillId Skill ID
     * @return 解析后的路径
     */
    private Path resolveSkillPath(String skillId) {
        if (StringUtils.isBlank(skillId)) {
            throw new IllegalArgumentException("skillId is required");
        }
        // 直接使用 skillId 作为目录名
        return extractedPath.resolve(skillId);
    }

    /**
     * 移动解压后的Skill到正式目录
     *
     * @param skillId Skill ID (格式: author/name 或 name)
     * @param tempExtractPath 临时解压路径
     * @return 正式目录路径
     */
    public String moveToExtracted(String skillId, String tempExtractPath) throws IOException {
        Path sourcePath = Paths.get(tempExtractPath);
        Path targetPath = resolveSkillPath(skillId);

        // 删除已存在的目录
        if (Files.exists(targetPath)) {
            deleteDirectory(targetPath);
        }

        // 创建父目录
        Files.createDirectories(targetPath.getParent());

        // 移动文件
        Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);

        log.info("Moved skill {} to extracted directory: {}", skillId, targetPath);
        return targetPath.toString();
    }

    /**
     * 获取Skill脚本目录
     *
     * @param skillId Skill ID (格式: author/name 或 name)
     * @return 脚本目录路径，如果不存在返回null
     */
    public String getSkillScriptsPath(String skillId) {
        try {
            Path scriptsPath = resolveSkillPath(skillId).resolve("scripts");
            if (Files.exists(scriptsPath) && Files.isDirectory(scriptsPath)) {
                return scriptsPath.toString();
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid skillId format: {}", skillId);
        }
        return null;
    }

    /**
     * 获取Skill文件路径
     *
     * @param skillId Skill ID (格式: author/name 或 name)
     * @param relativePath 相对路径 (如 "scripts/validate.py")
     * @return 完整路径，如果不存在返回null
     */
    public String getSkillFilePath(String skillId, String relativePath) {
        try {
            Path filePath = resolveSkillPath(skillId).resolve(relativePath);
            if (Files.exists(filePath)) {
                return filePath.toString();
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid skillId format: {}", skillId);
        }
        return null;
    }

    /**
     * 读取Skill文件内容
     *
     * @param skillId Skill ID
     * @param relativePath 相对路径
     * @return 文件内容，如果不存在返回null
     */
    public String readSkillFile(String skillId, String relativePath) {
        String filePath = getSkillFilePath(skillId, relativePath);
        if (filePath == null) {
            return null;
        }

        try {
            return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read skill file: {}/{}", skillId, relativePath, e);
            return null;
        }
    }

    public String readTempSkillFile(String tempExtractPath) {
        try {
            return new String(Files.readAllBytes(Paths.get(tempExtractPath + "/SKILL.md")), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read skill file: {}", tempExtractPath + "/SKILL.md", e);
            return null;
        }
    }

    /**
     * 删除Skill的所有文件
     *
     * @param skillId Skill ID (格式: author/name 或 name)
     */
    public void deleteSkillFiles(String skillId) throws IOException {
        try {
            // 删除解压目录
            Path skillPath = resolveSkillPath(skillId);
            if (Files.exists(skillPath)) {
                deleteDirectory(skillPath);
            }

            // 删除上传的zip
            Path zipPath = uploadedPath.resolve(skillId + ".zip");
            if (Files.exists(zipPath)) {
                Files.delete(zipPath);
            }

            log.info("Deleted skill files for: {}", skillId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid skillId format when deleting files: {}", skillId);
        }
    }

    /**
     * 清理临时目录
     */
    public void cleanupTemp() {
        try {
            Files.list(tempPath).forEach(path -> {
                try {
                    deleteDirectory(path);
                } catch (IOException e) {
                    log.warn("Failed to delete temp directory: {}", path, e);
                }
            });
            log.info("Cleaned up temp directory");
        } catch (IOException e) {
            log.error("Failed to cleanup temp directory", e);
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        Files.walk(path)
                .sorted((a, b) -> -a.compareTo(b)) // 反向排序，先删除子文件
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete: {}", p, e);
                    }
                });
    }

    /**
     * 获取Skill完整上下文信息，包括目录结构树和关键文件内容
     * 用于构建Skill执行时的prompt上下文
     *
     * @param skillId Skill ID
     * @return 格式化的Skill上下文字符串，包含目录树和文件内容；如果Skill目录不存在返回空字符串
     */
    public String getSkillContext(String skillId) {
        try {
            Path skillPath = resolveSkillPath(skillId);
            if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
                log.warn("Skill directory not found: {}", skillPath);
                return "";
            }

            StringBuilder sb = new StringBuilder();

            // 1. 目录结构树
            sb.append("### Directory Structure\n");
            sb.append("```\n");
            sb.append(buildDirectoryTree(skillPath, skillPath, ""));
            sb.append("```\n\n");

            // 2. 读取关键文件内容
            // SKILL.md
            appendFileContent(sb, skillPath, "SKILL.md");
            // reference.md
            appendFileContent(sb, skillPath, "reference.md");
            // examples.md
            appendFileContent(sb, skillPath, "examples.md");

            // 3. 读取 scripts/ 目录下所有脚本文件
            Path scriptsPath = skillPath.resolve("scripts");
            if (Files.exists(scriptsPath) && Files.isDirectory(scriptsPath)) {
                try (Stream<Path> scriptFiles = Files.walk(scriptsPath)) {
                    List<Path> scripts = scriptFiles
                            .filter(Files::isRegularFile)
                            .sorted()
                            .collect(Collectors.toList());
                    for (Path script : scripts) {
                        String relativePath = skillPath.relativize(script).toString();
                        appendFileContent(sb, skillPath, relativePath);
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to build skill context for: {}", skillId, e);
            return "";
        }
    }

    /**
     * 构建目录树字符串
     */
    private String buildDirectoryTree(Path root, Path current, String prefix) {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> children = Files.list(current)) {
            List<Path> sorted = children.sorted().collect(Collectors.toList());
            for (int i = 0; i < sorted.size(); i++) {
                Path child = sorted.get(i);
                boolean isLast = (i == sorted.size() - 1);
                String connector = isLast ? "└── " : "├── ";
                String childPrefix = isLast ? "    " : "│   ";

                sb.append(prefix).append(connector).append(child.getFileName());
                if (Files.isDirectory(child)) {
                    sb.append("/\n");
                    sb.append(buildDirectoryTree(root, child, prefix + childPrefix));
                } else {
                    sb.append("\n");
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list directory: {}", current, e);
        }
        return sb.toString();
    }

    /**
     * 将文件内容追加到StringBuilder中
     */
    private void appendFileContent(StringBuilder sb, Path skillPath, String relativePath) {
        Path filePath = skillPath.resolve(relativePath);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return;
        }
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            // 跳过过大的文件（超过10KB）
            if (content.length() > 10240) {
                sb.append("### File: ").append(relativePath).append("\n");
                sb.append("(File too large, ").append(content.length()).append(" bytes, truncated to first 10KB)\n");
                content = content.substring(0, 10240) + "\n... (truncated)";
            }
            String ext = getFileExtension(relativePath);
            sb.append("### File: ").append(relativePath).append("\n");
            sb.append("```").append(ext).append("\n");
            sb.append(content);
            if (!content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("```\n\n");
        } catch (IOException e) {
            log.warn("Failed to read file: {}", filePath, e);
        }
    }

    /**
     * 获取文件扩展名（用于代码块语法高亮）
     */
    private String getFileExtension(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0) return "";
        String ext = path.substring(dotIndex + 1).toLowerCase();
        switch (ext) {
            case "py": return "python";
            case "sh": return "bash";
            case "js": return "javascript";
            case "ts": return "typescript";
            case "md": return "markdown";
            case "yml":
            case "yaml": return "yaml";
            case "json": return "json";
            default: return ext;
        }
    }

    /**
     * 获取Skill的支持文件列表（scripts目录下所有文件 + reference.md / examples.md）
     * 用于在上下文中告知LLM有哪些文件可以请求查看
     *
     * @param skillId Skill ID
     * @return 相对路径列表，如 ["scripts/run.sh", "scripts/helper.py", "reference.md"]
     */
    public List<String> listSupportFiles(String skillId) {
        List<String> files = new ArrayList<>();
        try {
            Path skillPath = resolveSkillPath(skillId);
            if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
                return files;
            }

            // reference.md / examples.md
            for (String doc : Arrays.asList("reference.md", "examples.md")) {
                if (Files.exists(skillPath.resolve(doc))) {
                    files.add(doc);
                }
            }

            // scripts/ 目录
            Path scriptsPath = skillPath.resolve("scripts");
            if (Files.exists(scriptsPath) && Files.isDirectory(scriptsPath)) {
                try (Stream<Path> stream = Files.walk(scriptsPath)) {
                    stream.filter(Files::isRegularFile)
                            .sorted()
                            .forEach(p -> files.add(skillPath.relativize(p).toString()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list support files for skill: {}", skillId, e);
        }
        return files;
    }

    /**
     * 读取 SKILL.md 内容
     *
     * @param skillId Skill ID
     * @return SKILL.md 内容，不存在时返回空字符串
     */
    public String readSkillMd(String skillId) {
        String content = readSkillFile(skillId, "SKILL.md");
        return content != null ? content : "";
    }

    /**
     * 将单个支持文件格式化为适合注入上下文的字符串
     *
     * @param skillId      Skill ID
     * @param relativePath 相对路径（来自 listSupportFiles 的结果）
     * @return 格式化的文件内容字符串，文件不存在或读取失败时返回空字符串
     */
    public String formatSupportFile(String skillId, String relativePath) {
        Path skillPath = resolveSkillPath(skillId);
        StringBuilder sb = new StringBuilder();
        appendFileContent(sb, skillPath, relativePath);
        return sb.toString();
    }

    /**
     * 获取存储路径
     */
    public String getStoragePath() {
        return storagePath;
    }

    /**
     * 获取解压路径
     */
    public String getExtractedPath() {
        return extractedPath.toString();
    }
}
