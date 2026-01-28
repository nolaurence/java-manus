package cn.nolaurene.cms.service.sandbox.backend.utils;

import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromptRenderer {

    public static String render(String input, Map<String, Object> context) {
        // 定义正则表达式，匹配 ${key} 格式
        Pattern pattern = Pattern.compile("\\$\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = context.get(key);

            String replacement;
            if (value != null) {
                replacement = value.toString();
            } else {
                replacement = matcher.group(0); // 保留原始格式
            }

            // 对替换字符串进行转义，防止特殊字符（如 $ 和 \）引发问题
            replacement = Matcher.quoteReplacement(replacement);

            matcher.appendReplacement(result, replacement);
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public static List<ChatMessage> removeSystemPrompt(List<ChatMessage> memory) {
        // deep copy list
        if (memory == null || memory.isEmpty()) {
            return memory;
        }

        ArrayList<ChatMessage> resultList = new ArrayList<>(memory);
        // remove system prompt from memory
        resultList.removeIf(message -> message.getRole() == ChatMessage.Role.system);

        return resultList;
    }

    public static String renderHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : history) {
            sb.append(message.getRole().name()).append(": ").append(message.getContent()).append("\n");
        }
        return sb.toString();
    }

    public static String renderStep(List<Step> steps) {
        if (CollectionUtils.isEmpty(steps)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Step step : steps) {
            sb.append(String.format("Step description: %s; Status: %s, Result: %s\n", step.getDescription(), step.getStatus(), step.getResult()));
        }
        return sb.toString();
    }
}
