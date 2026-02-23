package cn.nolaurene.cms.service.sandbox.worker.shell.tools;

import cn.nolaurene.cms.common.sandbox.worker.resp.shell.*;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import cn.nolaurene.cms.service.sandbox.worker.shell.ShellService;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/12/29
 * @description: Shell tool class, providing Shell interaction related functions
 */
public class ShellTool {

    private final ShellService shellService;

    public ShellTool(ShellService shellService) {
        this.shellService = shellService;
    }

    // ==================== Shell Exec Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShellExecInput {
        @FieldDescription("Unique identifier of the target shell session")
        private String id;

        @FieldDescription("Working directory for command execution (must use absolute path)")
        private String execDir;

        @FieldDescription("Shell command to execute")
        private String command;
    }

    public ToolFactory createShellExecToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ShellExecInput> schema = ToolSchema.<ShellExecInput>builder()
                    .name("shell_exec")
                    .title("Execute Shell Command")
                    .description("Execute commands in a specified shell session. Use for running code, installing packages, or managing files.")
                    .inputSchema(new ShellExecInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<ShellExecInput> handler = (context, params) -> {
                try {
                    List<String> code = List.of(
                            "// Execute Shell Command in session: " + params.getId(),
                            "Working directory: " + params.getExecDir(),
                            "Command: " + params.getCommand()
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            ShellCommandResult result = shellService.execCommand(
                                    params.getId(),
                                    params.getExecDir(),
                                    params.getCommand()
                            );

                            List<McpSchema.Content> outputList = new ArrayList<>();
                            outputList.add(new McpSchema.TextContent("Session ID: " + result.getSessionId()));
                            outputList.add(new McpSchema.TextContent("Command: " + result.getCommand()));
                            outputList.add(new McpSchema.TextContent("Status: " + result.getStatus()));
                            if (result.getOutput() != null) {
                                outputList.add(new McpSchema.TextContent("Output: " + result.getOutput()));
                            }
                            outputList.add(new McpSchema.TextContent("Return code: " + result.getReturncode()));

                            return new ToolActionResult(outputList);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to execute shell command", e);
                        }
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to prepare shell exec tool", e);
                }
            };

            return Tool.<ShellExecInput>builder()
                    .capability(ToolCapability.SHELL)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== Shell View Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShellViewInput {
        @FieldDescription("Unique identifier of the target shell session")
        private String id;
    }

    public ToolFactory createShellViewToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ShellViewInput> schema = ToolSchema.<ShellViewInput>builder()
                    .name("shell_view")
                    .title("View Shell Session")
                    .description("View the content of a specified shell session. Use for checking command execution results or monitoring output.")
                    .inputSchema(new ShellViewInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<ShellViewInput> handler = (context, params) -> {
                try {
                    List<String> code = List.of(
                            "// View Shell session: " + params.getId()
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            ShellViewResult result = shellService.viewShell(params.getId());

                            List<McpSchema.Content> outputList = new ArrayList<>();
                            outputList.add(new McpSchema.TextContent("Session ID: " + result.getSessionId()));
                            outputList.add(new McpSchema.TextContent("Output: " + result.getOutput()));

                            return new ToolActionResult(outputList);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to view shell session", e);
                        }
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to prepare shell view tool", e);
                }
            };

            return Tool.<ShellViewInput>builder()
                    .capability(ToolCapability.SHELL)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== Shell Wait Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShellWaitInput {
        @FieldDescription("Unique identifier of the target shell session")
        private String id;

        @FieldDescription("Wait duration in seconds")
        private Integer seconds;
    }

    public ToolFactory createShellWaitToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ShellWaitInput> schema = ToolSchema.<ShellWaitInput>builder()
                    .name("shell_wait")
                    .title("Wait for Shell Process")
                    .description("Wait for the running process in a specified shell session to return. Use after running commands that require longer runtime.")
                    .inputSchema(new ShellWaitInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<ShellWaitInput> handler = (context, params) -> {
                try {
                    int waitSeconds = params.getSeconds() != null ? params.getSeconds() : 5;
                    
                    List<String> code = List.of(
                            "// Wait for process in session: " + params.getId(),
                            "Wait time: " + waitSeconds + " seconds"
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            boolean completed = shellService.waitForProcess(params.getId(), waitSeconds);

                            List<McpSchema.Content> outputList = new ArrayList<>();
                            outputList.add(new McpSchema.TextContent("Session ID: " + params.getId()));
                            outputList.add(new McpSchema.TextContent("Process completed: " + completed));

                            if (completed) {
                                ShellViewResult viewResult = shellService.viewShell(params.getId());
                                outputList.add(new McpSchema.TextContent("Output: " + viewResult.getOutput()));
                            }

                            return new ToolActionResult(outputList);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to wait for process", e);
                        }
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to prepare shell wait tool", e);
                }
            };

            return Tool.<ShellWaitInput>builder()
                    .capability(ToolCapability.SHELL)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== Shell Write To Process Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShellWriteInput {
        @FieldDescription("Unique identifier of the target shell session")
        private String id;

        @FieldDescription("Input content to write to the process")
        private String input;

        @FieldDescription("Whether to press Enter key after input")
        private Boolean pressEnter;
    }

    public ToolFactory createShellWriteToProcessToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ShellWriteInput> schema = ToolSchema.<ShellWriteInput>builder()
                    .name("shell_write_to_process")
                    .title("Write to Shell Process")
                    .description("Write input to a running process in a specified shell session. Use for responding to interactive command prompts.")
                    .inputSchema(new ShellWriteInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<ShellWriteInput> handler = (context, params) -> {
                try {
                    boolean pressEnter = params.getPressEnter() != null ? params.getPressEnter() : false;
                    
                    List<String> code = List.of(
                            "// Write to process in session: " + params.getId(),
                            "Input: " + params.getInput(),
                            "Press Enter: " + pressEnter
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            ShellWriteResult result = shellService.writeToProcess(
                                    params.getId(),
                                    params.getInput(),
                                    pressEnter
                            );

                            List<McpSchema.Content> outputList = new ArrayList<>();
                            outputList.add(new McpSchema.TextContent("Status: " + result.getStatus()));

                            return new ToolActionResult(outputList);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to write to process", e);
                        }
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to prepare shell write tool", e);
                }
            };

            return Tool.<ShellWriteInput>builder()
                    .capability(ToolCapability.SHELL)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    // ==================== Shell Kill Process Tool ====================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShellKillInput {
        @FieldDescription("Unique identifier of the target shell session")
        private String id;
    }

    public ToolFactory createShellKillProcessToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ShellKillInput> schema = ToolSchema.<ShellKillInput>builder()
                    .name("shell_kill_process")
                    .title("Kill Shell Process")
                    .description("Terminate a running process in a specified shell session. Use for stopping long-running processes or handling frozen commands.")
                    .inputSchema(new ShellKillInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<ShellKillInput> handler = (context, params) -> {
                try {
                    List<String> code = List.of(
                            "// Kill process in session: " + params.getId()
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            ShellKillResult result = shellService.killProcess(params.getId());

                            List<McpSchema.Content> outputList = new ArrayList<>();
                            outputList.add(new McpSchema.TextContent("Status: " + result.getStatus()));
                            outputList.add(new McpSchema.TextContent("Return code: " + result.getReturncode()));

                            return new ToolActionResult(outputList);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to kill process", e);
                        }
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to prepare shell kill tool", e);
                }
            };

            return Tool.<ShellKillInput>builder()
                    .capability(ToolCapability.SHELL)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    /**
     * Get all shell tools
     * 
     * @param captureSnapshot Whether to capture snapshots
     * @return List of all shell tools
     */
    public List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(
                createShellExecToolFactory().createTool(captureSnapshot),
                createShellViewToolFactory().createTool(captureSnapshot),
                createShellWaitToolFactory().createTool(captureSnapshot),
                createShellWriteToProcessToolFactory().createTool(captureSnapshot),
                createShellKillProcessToolFactory().createTool(captureSnapshot)
        );
    }
}
