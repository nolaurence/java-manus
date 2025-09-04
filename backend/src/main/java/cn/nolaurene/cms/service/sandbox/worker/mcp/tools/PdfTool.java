package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import com.microsoft.playwright.Page;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/7/11 下午4:00
 * @description:
 */
public class PdfTool {

    @Data
    public static class PdfSchema {

        @FieldDescription("File name to save the pdf to. Defaults to `page-{timestamp}.pdf` if not specified.")
        private String filename;
    }

    public static ToolFactory createPdfToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<PdfSchema> schema = ToolSchema.<PdfSchema>builder()
                    .name("browser_pdf_save")
                    .title("Save as PDF")
                    .description("Save page as PDF")
                    .inputSchema(new PdfSchema())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<PdfSchema> handler = (context, params) -> {
                try {
                    Tab tab = context.currentTabOrDie();
                    String filename = context.getConfig().getOutputFile(
                            StringUtils.isBlank(params.getFilename()) ? String.format("page-%s.pdf", getISOTime()) : null);

                    List<String> code = List.of(
                            "// Save page as " + filename,
                            String.format("await page.pdf(%s);", filename)
                    );

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.runAsync(() -> {
                        tab.getPage().pdf(new Page.PdfOptions().setPath(Path.of(filename)));
                    }).thenApply(v -> ToolActionResult.empty());

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to get save pdf file", e);
                }
            };

            // Build and return the tool
            return Tool.<PdfSchema>builder()
                    .capability(ToolCapability.PDF)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createPdfToolFactory().createTool(false));
    }

    private static String getISOTime() {
        OffsetDateTime currentDateTime = LocalDateTime.now().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        String isoString = currentDateTime.format(isoFormatter);
        return isoString.replace("+00:00", "Z");
    }

    public static void main(String[] args) {
        System.out.println(getISOTime());
    }
}
