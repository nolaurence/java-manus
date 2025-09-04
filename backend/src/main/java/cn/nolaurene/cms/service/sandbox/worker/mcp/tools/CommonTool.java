package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author nolaurence
 * @date 2025/7/8 下午2:47
 * @description:
 */
public class CommonTool {

    public static ToolFactory createCloseToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<Void> schema = ToolSchema.<Void>builder()
                    .name("browser_close")
                    .title("Close browser")
                    .description("Close the page")
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<Void> handler = (context, params) -> {
                try {
                    context.close();
                    return ToolResult.builder()
                            .code(List.of("// Close the browser"))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to close browser");
                }
            };

            return Tool.<Void>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    @Data
    public static class ResizeInput {

        @FieldDescription("Width of the browser window")
        private Integer width;

        @FieldDescription("Height of the browser window")
        private Integer height;
    }

    public static ToolFactory createResizeToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ResizeInput> schema = ToolSchema.<ResizeInput>builder()
                    .name("browser_resize")
                    .title("Resize browser window")
                    .description("Resize the browser window")
                    .inputSchema(new ResizeInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<ResizeInput> handler = (context, params) -> {
                Tab tab = context.currentTabOrDie();
                tab.getPage().setViewportSize(params.getWidth(), params.getHeight());

                List<String> code = List.of(
                        String.format("// Resize browser window to %s x %s", params.getWidth(), params.getHeight()),
                        String.format("page.setViewportSize(%d, %d);", params.getWidth(), params.getHeight())
                );

                return ToolResult.builder()
                        .code(code)
                        .captureSnapshot(captureSnapshot)
                        .action(() -> CompletableFuture
                                .runAsync(() -> tab.getPage().setViewportSize(params.getWidth(), params.getHeight()))
                                .thenApply(v -> ToolActionResult.empty()))
                        .waitForNetwork(false)
                        .build();
            };


            return Tool.<ResizeInput>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(
                createCloseToolFactory().createTool(false),
                createResizeToolFactory().createTool(captureSnapshot)
        );
    }
}
