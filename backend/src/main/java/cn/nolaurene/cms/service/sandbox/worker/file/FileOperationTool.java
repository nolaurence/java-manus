package cn.nolaurene.cms.service.sandbox.worker.file;

import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author nolaurence
 * @date 2025/12/28
 * @description: File operation tool class, providing file read/write/search operations
 */
public class FileOperationTool {

    private final Object sandbox; // TODO: Replace with actual Sandbox type

    public FileOperationTool(Object sandbox) {
        this.sandbox = sandbox;
    }

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
                    // TODO: Call sandbox.fileRead() method
                    // return sandbox.fileRead(params.getFile(), params.getStartLine(), params.getEndLine(), params.getSudo());
                    
                    List<String> code = List.of(String.format("// Reading file: %s", params.getFile()));
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
                    // Prepare content with newlines
                    String finalContent = params.getContent();
                    if (Boolean.TRUE.equals(params.getLeadingNewline())) {
                        finalContent = "\n" + finalContent;
                    }
                    if (Boolean.TRUE.equals(params.getTrailingNewline())) {
                        finalContent = finalContent + "\n";
                    }

                    // TODO: Call sandbox.fileWrite() method
                    // return sandbox.fileWrite(params.getFile(), finalContent, params.getAppend(), false, false, params.getSudo());
                    
                    List<String> code = List.of(String.format("// Writing to file: %s", params.getFile()));
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
                    // TODO: Call sandbox.fileReplace() method
                    // return sandbox.fileReplace(params.getFile(), params.getOldStr(), params.getNewStr(), params.getSudo());
                    
                    List<String> code = List.of(String.format("// Replacing in file: %s", params.getFile()));
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
                    // TODO: Call sandbox.fileSearch() method
                    // return sandbox.fileSearch(params.getFile(), params.getRegex(), params.getSudo());
                    
                    List<String> code = List.of(String.format("// Searching in file: %s with pattern: %s", params.getFile(), params.getRegex()));
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
                    // TODO: Call sandbox.fileFind() method
                    // return sandbox.fileFind(params.getPath(), params.getGlob());
                    
                    List<String> code = List.of(String.format("// Finding files in: %s with pattern: %s", params.getPath(), params.getGlob()));
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
