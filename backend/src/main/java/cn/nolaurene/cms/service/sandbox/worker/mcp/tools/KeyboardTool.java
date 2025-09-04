package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/7/8 下午5:56
 * @description:
 */
public class KeyboardTool {

    @Data
    public static class PressKeyInput {
        @FieldDescription("Name of the key to press or a character to generate, such as `ArrowLeft` or `a`")
        private String key;
    }

    public static ToolFactory createPressKeyToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<PressKeyInput> schema = ToolSchema.<PressKeyInput>builder()
                    .name("browser_press_key")
                    .title("Press a key")
                    .description("Press a key on the keyboard")
                    .inputSchema(new PressKeyInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<PressKeyInput> handler = (context, params) -> {
                try {
                    Tab tab = context.currentTabOrDie();

                    List<String> code = List.of(
                            String.format("// Press %s", params.getKey()),
                            String.format("tab.getPage().keyboard().press(\"%s\");", params.getKey())
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.runAsync(() -> {
                        tab.getPage().keyboard().press(params.getKey());
                    }).thenApply(v -> ToolActionResult.empty());

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(true)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to press key", e);
                }
            };

            // Build and return the tool
            return Tool.<PressKeyInput>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createPressKeyToolFactory().createTool(captureSnapshot));
    }
}
