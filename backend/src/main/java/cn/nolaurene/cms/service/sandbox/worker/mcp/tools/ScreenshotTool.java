package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import cn.nolaurene.cms.service.sandbox.worker.mcp.snapshot.PageSnapshot;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotScale;
import com.microsoft.playwright.options.ScreenshotType;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/7/8 下午6:37
 * @description:
 */
public class ScreenshotTool {

    @Data
    @NoArgsConstructor
    public static class ScreenshotInput {
        @FieldDescription("Whether to return without compression (in PNG format). Default is false, which returns a JPEG image.")
        private boolean raw;

        @FieldDescription("File name to save the screenshot to. Defaults to `page-{timestamp}.{png|jpeg}` if not specified.")
        private String filename;

        @FieldDescription("Human-readable element description used to obtain permission to screenshot the element. If not provided, the screenshot will be taken of viewport. If element is provided, ref must be provided too.")
        private String element;

        @FieldDescription("Exact target element reference from the page snapshot. If not provided, the screenshot will be taken of viewport. If ref is provided, element must be provided too.")
        private String ref;

        public ScreenshotInput(boolean raw, String filename, String element, String ref) {
            if ((element == null || element.isEmpty()) ^ (ref == null || ref.isEmpty())) {
                throw new IllegalArgumentException("Both element and ref must be provided or neither.");
            }
            this.raw = raw;
            this.filename = filename;
            this.element = element;
            this.ref = ref;
        }
    }

    public static ToolFactory createScreenshotToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<ScreenshotInput> schema = ToolSchema.<ScreenshotInput>builder()
                    .name("browser_take_screenshot")
                    .title("Take a screenshot")
                    .description("Take a screenshot of the current page. You can't perform actions based on the screenshot, use browser_snapshot for actions.")
                    .inputSchema(new ScreenshotInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<ScreenshotInput> handler = (context, params) -> {
                try {
                    Tab tab = context.currentTabOrDie();
                    PageSnapshot snapshot = tab.snapshotOrDie();
                    String fileType = params.isRaw() ? "png" : "jpeg";
                    String fileName = context.getConfig().getOutputFile(null != params.getFilename()
                            ? params.getFilename()
                            : "page-" + LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME) + "." + fileType);
                    Page.ScreenshotOptions options = new Page.ScreenshotOptions()
                            .setType(ScreenshotType.valueOf(fileType))
                            .setQuality(fileType.equals("png") ? 0 : 50)
                            .setScale(ScreenshotScale.CSS)
                            .setPath(Path.of(fileName));

                    boolean isElementScreenshot = StringUtils.isNotBlank(params.getElement()) && StringUtils.isNotBlank(params.getRef());

                    List<String> code = new ArrayList<>(List.of(String.format("// Screenshot %s and save it as %s", isElementScreenshot ? params.getElement() : "viewport", fileName)));

                    Locator locator = StringUtils.isNotBlank(params.getRef())
                            ? snapshot.refLocator(StringUtils.isNotBlank(params.getElement()) ? params.getElement() : "", params.getRef())
                            : null;


                    if (null != locator) {
                        code.add(String.format("page.%s.screenshot(%s);", locator, options));
                    } else {
                        code.add(String.format("await page.screenshot(%s);", options));
                    }
                    boolean includeBase64 = context.clientSupportsImages();
                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.supplyAsync(() -> {
                        Locator.ScreenshotOptions locatorOptions = new Locator.ScreenshotOptions()
                                .setType(options.type)
                                .setQuality(options.quality)
                                .setScale(options.scale)
                                .setPath(options.path);
                        byte[] screenshot = null != locator ? locator.screenshot(locatorOptions) : tab.getPage().screenshot(options);
                        McpSchema.ImageContent imageContent = null;
                        if (includeBase64) {
                            imageContent = new McpSchema.ImageContent();
                            byte[] decodedBytes = Base64.getDecoder().decode(screenshot);
                            imageContent.setData(new String(decodedBytes, StandardCharsets.UTF_8));
                            imageContent.setMimeType(fileType.equals("png") ? "image/png" : "image/jpeg");
                        }
                        return new ToolActionResult(imageContent);
                    });

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(true)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to get screenshot", e);
                }
            };

            // Build and return the tool
            return Tool.<ScreenshotInput>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        // captureSnapshot is ignored here, since we want to capture the snapshot anyway
        return List.of(createScreenshotToolFactory().createTool(captureSnapshot));
    }
}
