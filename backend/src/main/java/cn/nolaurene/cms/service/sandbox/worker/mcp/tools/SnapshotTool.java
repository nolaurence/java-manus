package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import cn.nolaurene.cms.service.sandbox.worker.mcp.snapshot.PageSnapshot;
import com.alibaba.fastjson.JSON;
import com.microsoft.playwright.Locator;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/7/11 下午5:22
 * @description:
 */
public class SnapshotTool {

    public static ToolFactory createSnapshotToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<Void> schema = ToolSchema.<Void>builder()
                    .name("browser_snapshot")
                    .title("Page snapshot")
                    .description("Capture accessibility snapshot of the current page, this is better than screenshot")
                    .inputSchema(null)
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<Void> handler = (context, params) -> {
                try {
                    context.ensureTab();

                    return ToolResult.builder()
                            .code(List.of("// <internal code to capture accessibility snapshot>"))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to get snapshot", e);
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

    @Data
    public static class ElementSchema {

        @FieldDescription("Human-readable element description used to obtain permission to interact with the element")
        private String element;

        @FieldDescription("Exact target element reference from the page snapshot")
        private String ref;
    }

    public static ToolFactory createClickToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ElementSchema> schema = ToolSchema.<ElementSchema>builder()
                    .name("browser_click")
                    .title("Click")
                    .description("Perform click on a web page")
                    .inputSchema(new ElementSchema())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<ElementSchema> handler = (context, params) -> {
                try {
                    Tab tab = context.currentTabOrDie();
                    Locator locator = tab.snapshotOrDie().refLocator(params.getElement(), params.getRef());

                    List<String> code = List.of(
                            "// Click " + params.getElement(),
                            String.format("page.%s.click()", locator)
                    );

                    return ToolResult.builder()
                            .code(code)
                            .action(() -> CompletableFuture.runAsync((locator::click)).thenApply(v -> ToolActionResult.empty()))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(true)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to execute click ops", e);
                }
            };

            // Build and return the tool
            return Tool.<ElementSchema>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    @Data
    public static class DragSchema {

        @FieldDescription("Human-readable source element description used to obtain the permission to interact with the element")
        private String startElement;

        @FieldDescription("Exact source element reference from the page snapshot")
        private String startRef;

        @FieldDescription("Human-readable target element description used to obtain the permission to interact with the element")
        private String endElement;

        @FieldDescription("Exact target element reference from the page snapshot")
        private String endRef;
    }

    public static ToolFactory createDragToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<DragSchema> schema = ToolSchema.<DragSchema>builder()
                    .name("browser_drag")
                    .title("Drag mouse")
                    .description("Perform drag and drop between two elements")
                    .inputSchema(new DragSchema())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<DragSchema> handler = (context, params) -> {
                try {
                    PageSnapshot snapshot = context.currentTabOrDie().snapshotOrDie();
                    Locator startLocator = snapshot.refLocator(params.getStartElement(), params.getStartRef());
                    Locator endLocator = snapshot.refLocator(params.getEndElement(), params.getEndRef());

                    List<String> code = List.of(
                            "// Drag " + params.getStartElement() + " to " + params.getEndElement(),
                            "page." + startLocator + ".dragTo(page." + endLocator + ");"
                    );

                    return ToolResult.builder()
                            .code(code)
                            .action(() -> CompletableFuture.runAsync((() -> startLocator.dragTo(endLocator))).thenApply(v -> ToolActionResult.empty()))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(true)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to drag", e);
                }
            };

            // Build and return the tool
            return Tool.<DragSchema>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static ToolFactory createHoverToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ElementSchema> schema = ToolSchema.<ElementSchema>builder()
                    .name("browser_hover")
                    .title("Hover mouse")
                    .description("Hover over element on page")
                    .inputSchema(new ElementSchema())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<ElementSchema> handler = (context, params) -> {
                try {
                    PageSnapshot snapshot = context.currentTabOrDie().snapshotOrDie();
                    Locator locator = snapshot.refLocator(params.getElement(), params.getRef());

                    List<String> code = List.of(
                            "// Hover over " + params.getElement(),
                            "page." + locator + ".hover();"
                    );

                    return ToolResult.builder()
                            .code(code)
                            .action(() -> CompletableFuture.runAsync((locator::hover)).thenApply(v -> ToolActionResult.empty()))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(true)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to hover", e);
                }
            };

            // Build and return the tool
            return Tool.<ElementSchema>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class TypeSchema extends ElementSchema {
        @FieldDescription("Text to type into the element")
        private String text;

        @FieldDescription("Whether to submit entered text (press Enter after)")
        private boolean submit;

        @FieldDescription("Whether to type one character at a time. Useful for triggering key handlers in the page. By default entire text is filled in at once.")
        private boolean slowly;
    }

    public static ToolFactory createTypeToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<TypeSchema> schema = ToolSchema.<TypeSchema>builder()
                    .name("browser_type")
                    .title("Type text")
                    .description("Type text into editable element")
                    .inputSchema(new TypeSchema())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<TypeSchema> handler = (context, params) -> {
                try {
                    PageSnapshot snapshot = context.currentTabOrDie().snapshotOrDie();
                    Locator locator = snapshot.refLocator(params.getElement(), params.getRef());

                    List<String> code = new ArrayList<>();
                    List<Runnable> steps = new ArrayList<>();

                    if (params.isSlowly()) {
                        code.add("// Press \"" + params.getText() + "\" sequentially into \"" + params.getElement() + "\"");
                        code.add(String.format("page.%s.pressSequentially(\"%s\");", locator, params.getText()));
                        steps.add(() -> locator.pressSequentially(params.getText()));
                    } else {
                        code.add(String.format("// Fill \"%s\" into \"%s\"", params.getText(), params.getElement()));
                        code.add(String.format("page.%s.fill(\"%s\");", locator, params.getText()));
                        steps.add(() -> locator.fill(params.getText()));
                    }

                    if (params.isSubmit()) {
                        code.add("// Submit text");
                        code.add(String.format("page.%s.press(\"Enter\");", locator));
                        steps.add(() -> locator.press("Enter"));
                    }

                    return ToolResult.builder()
                            .code(code)
                            .action(() -> CompletableFuture.runAsync(() -> {
                                for (Runnable step : steps) {
                                    step.run();
                                }
                            }).thenApply(v -> ToolActionResult.empty()))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(true)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to type", e);
                }
            };

            // Build and return the tool
            return Tool.<TypeSchema>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class SelectOptionSchema extends ElementSchema {
        @FieldDescription("Array of values to select in the dropdown. This can be a single value or multiple values.")
        private List<String> values;
    }

    public static ToolFactory createSelectOptionToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<SelectOptionSchema> schema = ToolSchema.<SelectOptionSchema>builder()
                    .name("browser_select_option")
                    .title("Select option")
                    .description("Select an option in a dropdown")
                    .inputSchema(new SelectOptionSchema())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<SelectOptionSchema> handler = (context, params) -> {
                try {
                    PageSnapshot snapshot = context.currentTabOrDie().snapshotOrDie();
                    Locator locator = snapshot.refLocator(params.getElement(), params.getRef());

                    List<String> code = List.of(
                            String.format("// Select options [%s] in %s", String.join(", ", params.getValues()), params.getElement()),
                            String.format("page.%s.selectOption(%s);", locator, JSON.toJSONString(params.getValues()))
                    );

                    return ToolResult.builder()
                            .code(code)
                            .action(() -> CompletableFuture.runAsync(() -> locator.selectOption(params.getValues().toArray(String[]::new))).thenApply(v -> ToolActionResult.empty()))
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(true)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to select option", e);
                }
            };

            // Build and return the tool
            return Tool.<SelectOptionSchema>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createSnapshotToolFactory().createTool(true),
                createClickToolFactory().createTool(true),
                createDragToolFactory().createTool(true),
                createHoverToolFactory().createTool(true),
                createTypeToolFactory().createTool(true),
                createSelectOptionToolFactory().createTool(true));
    }
}
