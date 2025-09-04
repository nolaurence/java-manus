package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import com.microsoft.playwright.ConsoleMessage;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author nolaurence
 * @date 2025/7/8 下午3:20
 * @description:
 */
public class ConsoleTool {

    public static ToolFactory createConsoleToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<Void> schema = ToolSchema.<Void>builder()
                    .name("browser_console_messages")
                    .title("Get console messages")
                    .description("Returns all console messages")
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<Void> handler = (context, params) -> {
                try {
                    List<String> code = List.of("// <internal code to get console messages>");

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        McpSchema.TextContent textContent = new McpSchema.TextContent();
                        List<ConsoleMessage> messages = context.currentTabOrDie().consoleMessages();
                        String log = messages.stream()
                                .map(message -> String.format("[%s] %s", message.type().toUpperCase(), message.text()))
                                .collect(Collectors.joining("\n"));
                        textContent.setText(log);
                        return new ToolActionResult(textContent);
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Fail to get console message: ", e);
                }
            };

            return Tool.<Void>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createConsoleToolFactory().createTool(false));
    }
}
