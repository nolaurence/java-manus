package cn.nolaurene.cms.service.sandbox.worker.shell.tools;

import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
public class ShellExecTool {

    @Data
    public static class ShellExecInput {

        @FieldDescription("The command to execute in the shell")
        private String command;
    }

    public static ToolFactory shellExecuteTool() {
        return (captureSnapshot) -> {
            ToolSchema<ShellExecInput> schema = ToolSchema.<ShellExecInput>builder()
                    .name("shell_execute")
                    .title("Execute Shell Command")
                    .description("Execute a command in the shell and return the output")
                    .inputSchema(new ShellExecInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<ShellExecInput> handler = (context, params) -> {
                if (StringUtils.isBlank(params.getCommand())) {
                    throw new RuntimeException("The command is empty");
                }
                StringBuilder output = new StringBuilder();
                try {
                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", params.getCommand());
                    pb.directory(new File(System.getProperty("user.dir")));
                    Process process = pb.start();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    }

                    int exitCode = process.waitFor();

                    List<String> code = List.of(
                            "// Execute Shell Command:",
                            "sh -c " + params.getCommand()
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        List<McpSchema.Content> outputList = List.of(
                                new McpSchema.TextContent("success: " + (exitCode == 0)),
                                new McpSchema.TextContent("output: " + output),
                                new McpSchema.TextContent("exit code: " + exitCode)
                        );
                        return new ToolActionResult(outputList);
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };

            return Tool.<ShellExecInput>builder()
                    .schema(schema)
                    .handler(handler)
                    .capability(ToolCapability.CORE)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(shellExecuteTool().createTool(captureSnapshot));
    }
}
