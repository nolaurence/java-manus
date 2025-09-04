package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.server.RequestInfo;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author nolaurence
 * @date 2025/7/11 下午3:46
 * @description:
 */
public class NetworkTool {

    public static ToolFactory createRequestsToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<Void> schema = ToolSchema.<Void>builder()
                    .name("browser_network_requests")
                    .title("List network requests")
                    .description("Returns all network requests since loading the page")
                    .inputSchema(null)
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<Void> handler = (context, params) -> {
                try {
                    Map<String, RequestInfo> requests = context.currentTabOrDie().requests();
                    String log = requests.entrySet()
                            .stream()
                            .map(entry -> renderRequest(entry.getValue()))
                            .collect(Collectors.joining("\n"));

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        McpSchema.TextContent textContent = new McpSchema.TextContent(log);
                        return new ToolActionResult(textContent);
                    });

                    return ToolResult.builder()
                            .code(List.of("// <internal code to list network requests>"))
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to render requests", e);
                }
            };

            // Build and return the tool
            return Tool.<Void>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createRequestsToolFactory().createTool(false));
    }

    private static String renderRequest(RequestInfo requestInfo) {
        ArrayList<String> result = new ArrayList<>();
        result.add(String.format("[%s] %s", requestInfo.getMethod().toUpperCase(), requestInfo.getUrl()));
        if (-1 != requestInfo.getRespStatus()) {
            result.add(String.format("=> [%s] %s", requestInfo.getRespStatus(), requestInfo.getRespStatusText()));
        }
        return String.join(" ", result);
    }
}
