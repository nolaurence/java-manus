package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nolaurence
 * @date 2025/7/11 下午4:50
 * @description:
 */
public class TestingTool {

    @Data
    public static class GenerateTestSchema {

        @FieldDescription("The name of the test")
        private String name;

        @FieldDescription("The description of the test")
        private String description;

        @FieldDescription("The steps of the test")
        private List<String> steps;
    }

    public static ToolFactory createGenerateTestToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<GenerateTestSchema> schema = ToolSchema.<GenerateTestSchema>builder()
                    .name("browser_generate_playwright_test")
                    .title("Generate a Playwright test")
                    .description("Generate a Playwright test for given scenario")
                    .inputSchema(new GenerateTestSchema())
                    .type(ToolType.READ_ONLY)
                    .build();

            ToolHandler<GenerateTestSchema> handler = (context, params) -> {
                try {
                    List<String> instructionList = new ArrayList<>(List.of(
                            "## Instructions",
                            "- You are a playwright test generator.",
                            "- You are given a scenario and you need to generate a playwright test for it.",
                            "- DO NOT generate test code based on the scenario alone. DO run steps one by one using the tools provided instead.",
                            "- Only after all steps are completed, emit a Playwright TypeScript test that uses @playwright/test based on message history",
                            "- Save generated test file in the tests directory",
                            "Test name: " + params.getName(),
                            "Description: " + params.getDescription(),
                            "Steps:"
                    ));
                    for (int i = 0; i < params.getSteps().size(); i++) {
                        instructionList.add(String.format("- %d. %s", i + 1, params.getSteps().get(i)));
                    }

                    return ToolResult.builder()
                            .code(new ArrayList<>())
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .resultOverride(new ToolActionResult(new McpSchema.TextContent(String.join("\n", instructionList))))
                            .build();

                } catch (Exception e) {
                    throw new RuntimeException("Failed to get list tabs", e);
                }
            };

            // Build and return the tool
            return Tool.<GenerateTestSchema>builder()
                    .capability(ToolCapability.TESTING)
                    .schema(schema)
                    .handler(handler)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createGenerateTestToolFactory().createTool(false));
    }
}
