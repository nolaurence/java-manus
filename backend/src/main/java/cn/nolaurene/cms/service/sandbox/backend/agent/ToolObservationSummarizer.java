package cn.nolaurene.cms.service.sandbox.backend.agent;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

final class ToolObservationSummarizer {

    private static final int MAX_BROWSER_SUMMARY_CHARS = 4_000;
    private static final int MAX_BROWSER_LINES = 40;
    private static final int MAX_IMPORTANT_LINES = 24;

    private ToolObservationSummarizer() {
    }

    static String forPersistence(String toolName, String observation) {
        if (!isBrowserTool(toolName) || StringUtils.isBlank(observation)) {
            return observation;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("[Browser tool result summarized for persistence]\n");
        summary.append("Tool: ").append(toolName).append("\n");
        summary.append("Original length: ").append(observation.length())
                .append(" chars, ").append(observation.lines().count()).append(" lines\n");

        appendMatchingLine(summary, observation, "- Page URL:");
        appendMatchingLine(summary, observation, "- Page Title:");

        List<String> importantLines = collectImportantBrowserLines(observation);
        if (!importantLines.isEmpty()) {
            summary.append("\nKey browser output:\n");
            for (String line : importantLines) {
                if (summary.length() >= MAX_BROWSER_SUMMARY_CHARS) {
                    break;
                }
                summary.append("- ").append(truncate(StringUtils.normalizeSpace(line), 220)).append("\n");
            }
        }

        if (observation.contains("`- Page Snapshot") || observation.contains("```yaml")) {
            summary.append("\nRaw page snapshot omitted. Re-run browser_snapshot when fresh element refs are needed.\n");
        } else {
            summary.append("\nRaw browser output omitted from persistence.\n");
        }

        return truncate(summary.toString(), MAX_BROWSER_SUMMARY_CHARS);
    }

    private static boolean isBrowserTool(String toolName) {
        return toolName != null && toolName.startsWith("browser_");
    }

    private static void appendMatchingLine(StringBuilder summary, String observation, String prefix) {
        observation.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .ifPresent(line -> summary.append(line.substring(2)).append("\n"));
    }

    private static List<String> collectImportantBrowserLines(String observation) {
        List<String> important = new ArrayList<>();
        observation.lines()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .filter(ToolObservationSummarizer::isImportantBrowserLine)
                .limit(MAX_BROWSER_LINES)
                .forEach(important::add);

        if (important.size() > MAX_IMPORTANT_LINES) {
            return important.subList(0, MAX_IMPORTANT_LINES);
        }
        return important;
    }

    private static boolean isImportantBrowserLine(String line) {
        String lower = line.toLowerCase();
        return line.contains("[ref=")
                || lower.contains("error")
                || lower.contains("exception")
                || lower.contains("failed")
                || lower.contains("warning")
                || lower.startsWith("[get]")
                || lower.startsWith("[post]")
                || lower.startsWith("[put]")
                || lower.startsWith("[delete]")
                || lower.startsWith("[console]")
                || lower.startsWith("[error]")
                || lower.startsWith("[warning]");
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n... (browser result truncated)";
    }
}
