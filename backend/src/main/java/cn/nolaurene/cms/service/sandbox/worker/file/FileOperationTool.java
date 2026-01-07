package cn.nolaurene.cms.service.sandbox.worker.file;

import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * @author nolaurence
 * @date 2025/12/28
 * @description: File operation tool class, providing file read/write/search operations
 */
public class FileOperationTool {

    // ==================== File Read Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileReadInput {
        @FieldDescription("Absolute path of the file to read")
        private String file;

        @FieldDescription("(Optional) Starting line to read from, 0-based")
        private Integer startLine;

        @FieldDescription("(Optional) Ending line number (exclusive)")
        private Integer endLine;

        @FieldDescription("(Optional) Whether to use sudo privileges")
        private Boolean sudo;
    }

    public static ToolFactory createFileReadToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<FileReadInput> schema = ToolSchema.<FileReadInput>builder()
                    .name("file_read")
                    .title("Read file content")
                    .description("Read file content. Use for checking file contents, analyzing logs, or reading configuration files.")
                    .inputSchema(new FileReadInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<FileReadInput> handler = (context, params) -> {
                try {
                    // 直接按照参数读取本地的文件
                    String file = params.getFile();
                    boolean sudo = params.getSudo() != null && params.getSudo();
                    
                    if (!sudo && !Files.exists(Paths.get(file))) {
                        throw new RuntimeException("File not found: " + file);
                    }

                    String content;
                    if (sudo) {
                        ProcessBuilder pb = new ProcessBuilder("sudo", "cat", file);
                        pb.redirectErrorStream(true);
                        Process process = pb.start();

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            content = sb.toString();
                        }

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            throw new RuntimeException("Failed to read file with sudo");
                        }
                    } else {
                        content = new String(Files.readAllBytes(Paths.get(file)));
                    }

                    // 处理行范围
                    if (params.getStartLine() != null || params.getEndLine() != null) {
                        String[] lines = content.split("\n");
                        int start = (params.getStartLine() != null) ? params.getStartLine() : 0;
                        int end = (params.getEndLine() != null) ? params.getEndLine() : lines.length;
                        content = String.join("\n", Arrays.asList(lines).subList(start, end));
                    }
                    
                    List<String> code = List.of(
                            String.format("// File: %s", file),
                            String.format("// Content:\n%s", content)
                    );
                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read file: ", e);
                }
            };

            return Tool.<FileReadInput>builder()
                    .capability(ToolCapability.FILES)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== File Write Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileWriteInput {
        @FieldDescription("Absolute path of the file to write to")
        private String file;

        @FieldDescription("Text content to write. This string will be embedded as a JSON string value, so any characters that require JSON escaping (such as double quotes \\\", backslashes \\\\, newlines \\\\n, etc.) MUST be properly escaped using standard JSON escape sequences. For example, a literal double quote should be written as \\\\\\\" and a backslash as \\\\\\\\.")
        private String content;

        @FieldDescription("(Optional) Whether to use append mode")
        private Boolean append;

        @FieldDescription("(Optional) Whether to add a leading newline")
        private Boolean leadingNewline;

        @FieldDescription("(Optional) Whether to add a trailing newline")
        private Boolean trailingNewline;

        @FieldDescription("(Optional) Whether to use sudo privileges")
        private Boolean sudo;
    }

    public static ToolFactory createFileWriteToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<FileWriteInput> schema = ToolSchema.<FileWriteInput>builder()
                    .name("file_write")
                    .title("Write to file")
                    .description("Overwrite or append content to a file. Use for creating new files, appending content, or modifying existing files.")
                    .inputSchema(new FileWriteInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<FileWriteInput> handler = (context, params) -> {
                try {
                    // 直接调用本地文件写入
                    String file = params.getFile();
                    String fileContent = params.getContent();
                    boolean append = params.getAppend() != null && params.getAppend();
                    boolean leadingNewline = params.getLeadingNewline() != null && params.getLeadingNewline();
                    boolean trailingNewline = params.getTrailingNewline() != null && params.getTrailingNewline();
                    boolean sudo = params.getSudo() != null && params.getSudo();
                    
                    String contentToWrite = fileContent;
                    // 处理换行符
                    if (leadingNewline) {
                        contentToWrite = "\n" + fileContent;
                    }
                    if (trailingNewline) {
                        contentToWrite = fileContent + "\n";
                    }

                    long bytesWritten = 0;
                    if (sudo) {
                        // 创建临时文件
                        Path tempFile = Files.createTempFile("file_write_", ".tmp");
                        Files.write(tempFile, contentToWrite.getBytes(StandardCharsets.UTF_8));

                        ProcessBuilder pb = new ProcessBuilder("sudo", "bash", "-c",
                                "cat " + tempFile.toString() + " " + (append ? ">>" : ">") + " '" + file + "'");
                        pb.redirectErrorStream(true);
                        Process process = pb.start();

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                // 日志记录
                            }
                        }

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            throw new RuntimeException("Failed to write file with sudo");
                        }

                        Files.deleteIfExists(tempFile);
                        bytesWritten = contentToWrite.getBytes(StandardCharsets.UTF_8).length;
                    } else {
                        // 创建目录
                        Path path = Paths.get(file).getParent();
                        if (path != null && !Files.exists(path)) {
                            Files.createDirectories(path);
                        }

                        // 写入文件
                        OpenOption[] options = append ?
                                new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND} :
                                new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

                        bytesWritten = Files.write(Paths.get(file), contentToWrite.getBytes(StandardCharsets.UTF_8), options).toFile().length();
                    }
                    
                    List<String> code = List.of(
                            String.format("// Written to file: %s", file),
                            String.format("// Bytes written: %d", bytesWritten)
                    );
                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to write file: ", e);
                }
            };

            return Tool.<FileWriteInput>builder()
                    .capability(ToolCapability.FILES)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== File String Replace Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileStrReplaceInput {
        @FieldDescription("Absolute path of the file to perform replacement on")
        private String file;

        @FieldDescription("Original string to be replaced")
        private String oldStr;

        @FieldDescription("New string to replace with")
        private String newStr;

        @FieldDescription("(Optional) Whether to use sudo privileges")
        private Boolean sudo;
    }

    public static ToolFactory createFileStrReplaceToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<FileStrReplaceInput> schema = ToolSchema.<FileStrReplaceInput>builder()
                    .name("file_str_replace")
                    .title("Replace string in file")
                    .description("Replace specified string in a file. Use for updating specific content in files or fixing errors in code.")
                    .inputSchema(new FileStrReplaceInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<FileStrReplaceInput> handler = (context, params) -> {
                try {
                    // 直接调用本地文件替换
                    String file = params.getFile();
                    String oldStr = params.getOldStr();
                    String newStr = params.getNewStr();
                    boolean sudo = params.getSudo() != null && params.getSudo();
                    
                    // 读取文件
                    if (!sudo && !Files.exists(Paths.get(file))) {
                        throw new RuntimeException("File not found: " + file);
                    }

                    String content;
                    if (sudo) {
                        ProcessBuilder pb = new ProcessBuilder("sudo", "cat", file);
                        pb.redirectErrorStream(true);
                        Process process = pb.start();

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            content = sb.toString();
                        }

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            throw new RuntimeException("Failed to read file with sudo");
                        }
                    } else {
                        content = new String(Files.readAllBytes(Paths.get(file)));
                    }

                    // 计算替换次数
                    int replacedCount = countOccurrences(content, oldStr);
                    if (replacedCount == 0) {
                        List<String> code = List.of(
                                String.format("// No replacements in file: %s", file),
                                String.format("// Occurrences replaced: 0")
                        );
                        return ToolResult.builder()
                                .code(code)
                                .captureSnapshot(captureSnapshot)
                                .waitForNetwork(false)
                                .build();
                    }

                    // 替换内容
                    String newContent = content.replace(oldStr, newStr);

                    // 写回文件
                    if (sudo) {
                        Path tempFile = Files.createTempFile("file_write_", ".tmp");
                        Files.write(tempFile, newContent.getBytes(StandardCharsets.UTF_8));

                        ProcessBuilder pb = new ProcessBuilder("sudo", "bash", "-c",
                                "cat " + tempFile.toString() + " > '" + file + "'");
                        pb.redirectErrorStream(true);
                        Process process = pb.start();

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                // 日志记录
                            }
                        }

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            throw new RuntimeException("Failed to write file with sudo");
                        }

                        Files.deleteIfExists(tempFile);
                    } else {
                        Files.write(Paths.get(file), newContent.getBytes(StandardCharsets.UTF_8), 
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                    
                    List<String> code = List.of(
                            String.format("// Replaced in file: %s", file),
                            String.format("// Occurrences replaced: %d", replacedCount)
                    );
                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to replace string in file: ", e);
                }
            };

            return Tool.<FileStrReplaceInput>builder()
                    .capability(ToolCapability.FILES)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== File Find In Content Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileFindInContentInput {
        @FieldDescription("Absolute path of the file to search within")
        private String file;

        @FieldDescription("Regular expression pattern to match")
        private String regex;

        @FieldDescription("(Optional) Whether to use sudo privileges")
        private Boolean sudo;
    }

    public static ToolFactory createFileFindInContentToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<FileFindInContentInput> schema = ToolSchema.<FileFindInContentInput>builder()
                    .name("file_find_in_content")
                    .title("Search in file content")
                    .description("Search for matching text within file content. Use for finding specific content or patterns in files.")
                    .inputSchema(new FileFindInContentInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<FileFindInContentInput> handler = (context, params) -> {
                try {
                    // 直接调用本地文件搜索
                    String file = params.getFile();
                    String regex = params.getRegex();
                    boolean sudo = params.getSudo() != null && params.getSudo();
                    
                    // 读取文件
                    if (!sudo && !Files.exists(Paths.get(file))) {
                        throw new RuntimeException("File not found: " + file);
                    }

                    String content;
                    if (sudo) {
                        ProcessBuilder pb = new ProcessBuilder("sudo", "cat", file);
                        pb.redirectErrorStream(true);
                        Process process = pb.start();

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            content = sb.toString();
                        }

                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            throw new RuntimeException("Failed to read file with sudo");
                        }
                    } else {
                        content = new String(Files.readAllBytes(Paths.get(file)));
                    }

                    // 编译正则表达式
                    Pattern pattern;
                    try {
                        pattern = Pattern.compile(regex);
                    } catch (PatternSyntaxException e) {
                        throw new RuntimeException("Invalid regular expression: " + e.getDescription());
                    }

                    // 查找匹配项
                    List<String> matches = new ArrayList<>();

                    String[] lines = content.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        if (pattern.matcher(lines[i]).find()) {
                            matches.add(lines[i]);
                        }
                    }
                    
                    List<String> code = List.of(
                            String.format("// Searched in file: %s", file),
                            String.format("// Found %d matches:", matches.size()),
                            matches.stream()
                                    .limit(10)
                                    .map(match -> String.format("//   %s", match))
                                    .collect(Collectors.joining("\n"))
                    );
                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to search in file: ", e);
                }
            };

            return Tool.<FileFindInContentInput>builder()
                    .capability(ToolCapability.FILES)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== File Find By Name Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileFindByNameInput {
        @FieldDescription("Absolute path of directory to search")
        private String path;

        @FieldDescription("Filename pattern using glob syntax wildcards")
        private String glob;
    }

    public static ToolFactory createFileFindByNameToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<FileFindByNameInput> schema = ToolSchema.<FileFindByNameInput>builder()
                    .name("file_find_by_name")
                    .title("Find files by name")
                    .description("Find files by name pattern in specified directory. Use for locating files with specific naming patterns.")
                    .inputSchema(new FileFindByNameInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<FileFindByNameInput> handler = (context, params) -> {
                try {
                    // 直接调用本地文件查找
                    String path = params.getPath();
                    String globPattern = params.getGlob();
                    
                    if (!Files.exists(Paths.get(path))) {
                        throw new RuntimeException("Directory does not exist: " + path);
                    }

                    // 使用Glob模式查找文件
                    List<String> files = new ArrayList<>();
                    Path rootPath = Paths.get(path);
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

                    Files.walk(rootPath)
                            .filter(pathObj -> matcher.matches(pathObj.getFileName()))
                            .forEach(pathObj -> files.add(pathObj.toString()));
                    
                    List<String> code = List.of(
                            String.format("// Found %d files in: %s", files.size(), path),
                            files.stream()
                                    .limit(20)
                                    .map(file -> String.format("//   %s", file))
                                    .collect(Collectors.joining("\n"))
                    );
                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to find files: ", e);
                }
            };

            return Tool.<FileFindByNameInput>builder()
                    .capability(ToolCapability.FILES)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    /**
     * Count occurrences of a substring in a string
     */
    private static int countOccurrences(String content, String sub) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(sub, index)) != -1) {
            count++;
            index += sub.length();
        }
        return count;
    }

    /**
     * Get all file operation tools
     * 
     * @param captureSnapshot Whether to capture snapshot
     * @return List of all file operation tools
     */
    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(
                createFileReadToolFactory().createTool(captureSnapshot),
                createFileWriteToolFactory().createTool(captureSnapshot),
                createFileStrReplaceToolFactory().createTool(captureSnapshot),
                createFileFindInContentToolFactory().createTool(captureSnapshot),
                createFileFindByNameToolFactory().createTool(captureSnapshot)
        );
    }
}
