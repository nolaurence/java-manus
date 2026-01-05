package cn.nolaurene.cms.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Fastjson2LenientToolParser {

    private static final String REGEX_PREFIX = "(\"(";
    private static final String REGEX_SUFFIX = ")\"\\s*:\\s*\")([^\"]*)";

    /**
     * 容错解析 arguments 字符串，自动修复 content 中未转义的双引号
     *
     * @param rawArguments 原始 arguments 字符串（可能非法）
     * @return 解析后的 JSONObject
     * @throws IllegalArgumentException 如果修复后仍无法解析
     */
    public static JSONObject parseArguments(String rawArguments, McpSchema.JsonSchema jsonSchema) {
        try {
            // 1. 先尝试直接解析（合法 JSON）
            return JSON.parseObject(rawArguments);
        } catch (Exception e) {
            // 2. 解析失败，按照inputSchema逐个字段修复
            if (null == jsonSchema) {
                throw new IllegalArgumentException("No inputSchema provided");
            }

            // 匹配 schema 中存在的字段... 但不贪婪，避免跨字段匹配
            Pattern compiledPattern = Pattern.compile(REGEX_PREFIX + String.join("|", jsonSchema.getProperties().keySet()) + REGEX_SUFFIX);
            String repaired = repairUnescapedQuotesInContent(rawArguments, compiledPattern);
            try {
                return JSON.parseObject(repaired);
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "Failed to parse or repair arguments: " + rawArguments, ex);
            }
        }
    }

    /**
     * 修复 content 字段中未转义的双引号。
     * 例如： "content":"print("Hello")" → "content":"print(\"Hello\")"
     */
    private static String repairUnescapedQuotesInContent(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1); // "content":"
            String value = matcher.group(2);  // print("Hello

            // 将未转义的 " 转为 \"
            String escapedValue = value.replace("\"", "\\\"");

            matcher.appendReplacement(sb, prefix + escapedValue);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static void main(String[] args) {
        String json = "{\n" +
                "  \"file\": \"/home/ubuntu/hello.py\",\n" +
                "  \"content\": \"print(\"Hello, World!\")\"\n" +
                "}";

        Pattern pattern = Pattern.compile(REGEX_PREFIX + "file|content" + REGEX_SUFFIX);
        Matcher matcher = pattern.matcher(json);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1); // "content":"
            String value = matcher.group(2);  // print("Hello

            // 将未转义的 " 转为 \"
            String escapedValue = value.replace("\"", "\\\"");

            matcher.appendReplacement(sb, prefix + escapedValue);
        }
        matcher.appendTail(sb);
        System.out.println(sb.toString());
    }
}
