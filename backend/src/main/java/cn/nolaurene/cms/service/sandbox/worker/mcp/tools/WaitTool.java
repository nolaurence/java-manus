package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nolaurence
 * @date 2025/7/11 下午5:02
 * @description:
 */
public class WaitTool {

    @Data
    public static class WaitInput {
        @FieldDescription("The time to wait in seconds")
        private int time;

        @FieldDescription("The text to wait for")
        private String text;

        @FieldDescription("The text to wait for to disappear")
        private String textGone;
    }

    public static ToolFactory createWaitToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<WaitInput> schema = ToolSchema.<WaitInput>builder()
                    .name("browser_wait_for")
                    .title("Wait for")
                    .description("Wait for text to appear or disappear or a specified time to pass")
                    .inputSchema(new WaitInput())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<WaitInput> handler = (context, params) -> {
                try {
                    if (StringUtils.isBlank(params.getText()) && StringUtils.isBlank(params.getTextGone()) && 0 >= params.getTime()) {
                        throw new RuntimeException("Either time, text or textGone must be provided");
                    }
                    List<String> code = new ArrayList<>();

                    if (0 < params.getTime()) {
                        code.add(String.format("Thread.sleep(%d * 1000)", params.getTime()));
                        Thread.sleep(Math.min(10000, params.getTime() * 1000));
                    }

                    Tab tab = context.currentTabOrDie();
                    Locator locator = StringUtils.isNotBlank(params.getText()) ? tab.getPage().getByText(params.getText()).first() : null;
                    Locator goneLocator = StringUtils.isNotBlank(params.getTextGone()) ? tab.getPage().getByText(params.getTextGone()).first() : null;

                    if (null != goneLocator) {
                        code.add(String.format("page.getByText(%s).first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN)", params.getTextGone()));
                        goneLocator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
                    }

                    if (null != locator) {
                        code.add(String.format("page.getByText(%s).first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN)", params.getText()));
                        locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                    }

                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to wait", e);
                }
            };

            // Build and return the tool
            return Tool.<WaitInput>builder()
                    .capability(ToolCapability.WAIT)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createWaitToolFactory().createTool(captureSnapshot));
    }
}
