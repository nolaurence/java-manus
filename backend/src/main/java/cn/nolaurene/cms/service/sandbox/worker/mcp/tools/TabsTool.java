package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import com.microsoft.playwright.Page;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/7/11 下午4:13
 * @description:
 */
public class TabsTool {

    public static ToolFactory createListTabsToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<Void> schema = ToolSchema.<Void>builder()
                    .name("browser_tab_list")
                    .title("List tabs")
                    .description("List browser tabs")
                    .inputSchema(null)
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<Void> handler = (context, params) -> {
                try {
                    context.ensureTab();

                    return ToolResult.builder()
                            .code(List.of("// <internal code to list tabs>"))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .resultOverride(new ToolActionResult(new McpSchema.TextContent(context.listTabsMarkdown())))
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to get list tabs", e);
                }
            };

            // Build and return the tool
            return Tool.<Void>builder()
                    .capability(ToolCapability.TABS)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    @Data
    public static class SelectTabSchema {

        @FieldDescription("The index of the tab to select")
        private int index;
    }

    public static ToolFactory createSelectTabToolFactory() {
        return (captureSnapshot) -> {

            ToolSchema<SelectTabSchema> schema = ToolSchema.<SelectTabSchema>builder()
                    .name("browser_tab_select")
                    .title("Select a tab")
                    .description("Select a tab by index")
                    .inputSchema(new SelectTabSchema())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<SelectTabSchema> handler = (context, params) -> {
                try {
                    context.selectTab(params.getIndex());

                    return ToolResult.builder()
                            .code(List.of(String.format("// <internal code to select tab %d>", params.getIndex())))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to get select tab", e);
                }
            };

            return Tool.<SelectTabSchema>builder()
                    .capability(ToolCapability.TABS)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    @Data
    public static class NewTabInput {

        @FieldDescription("The URL to navigate to in the new tab. If not provided, the new tab will be blank.")
        private String url;
    }

    public static ToolFactory createNewTabToolFactory() {
        return (captureSnapshot) -> {

            ToolSchema<NewTabInput> schema = ToolSchema.<NewTabInput>builder()
                    .name("browser_tab_new")
                    .title("Open a new tab")
                    .description("Open a new tab")
                    .inputSchema(new NewTabInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<NewTabInput> handler = (context, params) -> {
                try {
                    context.newTab();
                    if (StringUtils.isNotBlank(params.getUrl())) {
                        context.currentTabOrDie().navigate(params.getUrl());
                    }

                    return ToolResult.builder()
                            .code(List.of("// <internal code to open a new tab>"))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to create new tab", e);
                }
            };

            return Tool.<NewTabInput>builder()
                    .capability(ToolCapability.TABS)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    @Data
    public static class CloseTabInput {

        @FieldDescription("The index of the tab to close. Closes current tab if not provided.")
        private int index;
    }

    public static ToolFactory createCloseTabToolFactory() {
        return (captureSnapshot) -> {

            ToolSchema<CloseTabInput> schema = ToolSchema.<CloseTabInput>builder()
                    .name("browser_tab_close")
                    .title("Close a tab")
                    .description("Close a tab")
                    .inputSchema(new CloseTabInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<CloseTabInput> handler = (context, params) -> {
                try {
                    context.closeTab(params.getIndex());

                    return ToolResult.builder()
                            .code(List.of(String.format("// <internal code to close tab %s>", params.getIndex())))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to close tab", e);
                }
            };

            return Tool.<CloseTabInput>builder()
                    .capability(ToolCapability.TABS)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createListTabsToolFactory().createTool(false),
                createSelectTabToolFactory().createTool(captureSnapshot),
                createNewTabToolFactory().createTool(captureSnapshot),
                createCloseTabToolFactory().createTool(captureSnapshot));
    }
}
