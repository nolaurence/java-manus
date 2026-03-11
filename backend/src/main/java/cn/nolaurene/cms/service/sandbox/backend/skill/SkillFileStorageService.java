package cn.nolaurene.cms.service.sandbox.backend.skill;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
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
     * 移动解压后的Skill到正式目录
     *
     * @param skillId Skill ID (格式: author/name)
     * @param tempExtractPath 临时解压路径
     * @return 正式目录路径
     */
    public String moveToExtracted(String skillId, String tempExtractPath) throws IOException {
        Path sourcePath = Paths.get(tempExtractPath);

        // 解析 skillId 获取 author 和 name
        String[] parts = skillId.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid skillId format, expected 'author/name': " + skillId);
        }

        Path targetPath = extractedPath.resolve(parts[0]).resolve(parts[1]);

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
     * @param skillId Skill ID
     * @return 脚本目录路径，如果不存在返回null
     */
    public String getSkillScriptsPath(String skillId) {
        String[] parts = skillId.split("/");
        if (parts.length != 2) {
            return null;
        }

        Path scriptsPath = extractedPath.resolve(parts[0]).resolve(parts[1]).resolve("scripts");
        if (Files.exists(scriptsPath) && Files.isDirectory(scriptsPath)) {
            return scriptsPath.toString();
        }
        return null;
    }

    /**
     * 获取Skill文件路径
     *
     * @param skillId Skill ID
     * @param relativePath 相对路径 (如 "scripts/validate.py")
     * @return 完整路径，如果不存在返回null
     */
    public String getSkillFilePath(String skillId, String relativePath) {
        String[] parts = skillId.split("/");
        if (parts.length != 2) {
            return null;
        }

        Path filePath = extractedPath.resolve(parts[0]).resolve(parts[1]).resolve(relativePath);
        if (Files.exists(filePath)) {
            return filePath.toString();
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

    /**
     * 检查Skill文件是否存在
     *
     * @param skillId Skill ID
     * @param relativePath 相对路径
     * @return 是否存在
     */
    public boolean skillFileExists(String skillId, String relativePath) {
        return getSkillFilePath(skillId, relativePath) != null;
    }

    /**
     * 删除Skill的所有文件
     *
     * @param skillId Skill ID
     */
    public void deleteSkillFiles(String skillId) throws IOException {
        String[] parts = skillId.split("/");
        if (parts.length != 2) {
            return;
        }

        // 删除解压目录
        Path skillPath = extractedPath.resolve(parts[0]).resolve(parts[1]);
        if (Files.exists(skillPath)) {
            deleteDirectory(skillPath);
        }

        // 删除上传的zip
        Path zipPath = uploadedPath.resolve(skillId + ".zip");
        if (Files.exists(zipPath)) {
            Files.delete(zipPath);
        }

        log.info("Deleted skill files for: {}", skillId);
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
