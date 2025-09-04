package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author nolaurence
 * @date 2025/7/8 上午10:55
 * @description: Navigation tool implementation - Java version of navigate.ts
 *               This tool handles browser navigation operations.
 */
@Slf4j
public class NavigateTool {

    /**
     * Input schema for navigation tool
     */
    @Data
    public static class NavigateInput {

        @FieldDescription("The URL to navigate to")
        private String url;
    }

    /**
     * Creates a navigation tool factory that can be configured for snapshot capture
     */
    public static ToolFactory createNavigateToolFactory() {
        return (captureSnapshot) -> {
            // Define the schema
            ToolSchema<NavigateInput> schema = ToolSchema.<NavigateInput>builder()
                    .name("browser_navigate")
                    .title("Navigate to a URL")
                    .description("Navigate to a URL")
                    .inputSchema(new NavigateInput()) // In real implementation, this would be a proper schema
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            // Define the handler
            ToolHandler<NavigateInput> handler = (context, params) -> {
                try {
                    Tab tab = context.ensureTab();
                    log.info("[browser_navigate] page: {}", tab.getPage());
                    tab.navigate(params.getUrl());

                    // create code representation
                    List<String> code = List.of(
                            "// Navigate to " + params.getUrl(),
                            "page.navigate(\"" + params.getUrl() + "\");"
                    );

                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to navigate to " + params.getUrl(), e);
                }
            };
            return Tool.<NavigateInput>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    /**
     * Creates a go back tool factory.
     *
     * @return A ToolFactory that creates go back tools
     */
    public static ToolFactory createGoBackToolFactory() {
        return (captureSnapshot) -> {
            // Define the schema - go back takes no parameters
            ToolSchema<Void> schema = ToolSchema.<Void>builder()
                    .name("browser_navigate_back")
                    .title("Go back")
                    .description("Go back to the previous page")
                    .inputSchema(null)
                    .type(ToolType.READ_ONLY)
                    .build();

            // Define the handler
            ToolHandler<Void> handler = (context, params) -> {
                try {
                    // Get current tab - assuming we have a goBack method
                    Tab tab = context.ensureTab();
                    tab.getPage().goBack();

                    // Create code representation
                    List<String> code = List.of(
                            "// Go back in browser history",
                            "page.goBack();"
                    );

                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to go back", e);
                }
            };

            // Build and return the tool
            return Tool.<Void>builder()
                    .capability(ToolCapability.HISTORY)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static ToolFactory createGoForwardToolFactory() {
        return (captureSnapshot) -> {

            ToolSchema<Void> schema = ToolSchema.<Void>builder()
                    .name("browser_navigate_forward")
                    .title("Go forward")
                    .description("Go forward to the next page")
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<Void> handler = (context, params) -> {
                try {
                    Tab tab = context.currentTabOrDie();
                    tab.getPage().goForward();
                    List<String> code = List.of("// Navigate forward", "page.goForward();");

                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to go forward", e);
                }
            };
            return Tool.<Void>builder()
                    .capability(ToolCapability.HISTORY)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(
                createNavigateToolFactory().createTool(captureSnapshot),
                createGoBackToolFactory().createTool(captureSnapshot),
                createGoForwardToolFactory().createTool(captureSnapshot)
        );
    }

}
