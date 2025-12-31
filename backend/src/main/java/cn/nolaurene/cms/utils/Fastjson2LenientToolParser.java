package cn.nolaurene.cms.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Fastjson2LenientToolParser {

    // 匹配 "content":"... 但不贪婪，避免跨字段匹配
    private static final Pattern CONTENT_PATTERN =
            Pattern.compile("(\"(content|description|message|regex)\"\\s*:\\s*\")([^\"]*)");

    /**
     * 容错解析 arguments 字符串，自动修复 content 中未转义的双引号
     *
     * @param rawArguments 原始 arguments 字符串（可能非法）
     * @return 解析后的 JSONObject
     * @throws IllegalArgumentException 如果修复后仍无法解析
     */
    public static JSONObject parseArguments(String rawArguments) {
        try {
            // 1. 先尝试直接解析（合法 JSON）
            return JSON.parseObject(rawArguments);
        } catch (Exception e) {
            // 2. 解析失败，尝试修复 content 字段
            String repaired = repairUnescapedQuotesInContent(rawArguments);
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
    private static String repairUnescapedQuotesInContent(String json) {
        Matcher matcher = CONTENT_PATTERN.matcher(json);
        StringBuffer sb = new StringBuffer();

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
}
