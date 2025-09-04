package cn.nolaurene.cms.service.sandbox.backend.utils;

import cn.nolaurene.cms.common.sandbox.backend.model.Function;
import cn.nolaurene.cms.common.sandbox.backend.model.ToolCall;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONPath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public class ReActParser {

    private static final String CONTENT_PATH = "$.choices[0].message.content";
    private static final String THOUGHT_PATH = "$.choices[0].message.reasoning_content";

    private static final String THINK_START = "<think>\n";
    private static final String THINK_END = "\n</think>\n";

    public static String parseThinking(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }

        // 匹配 <think> 标签内的内容
        Pattern thinkPattern = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
        Matcher thinkMatcher = thinkPattern.matcher(content);

        if (thinkMatcher.find()) {
            return thinkMatcher.group(1).trim();
        }
        return "";
    }

    public static List<ToolCall> parseToolCallsFromContent(String content) {
        List<ToolCall> toolCalls = new ArrayList<>();
        Pattern toolUsePattern = Pattern.compile("<tool_use>(.*?)</tool_use>", Pattern.DOTALL);
        Matcher toolUseMatcher = toolUsePattern.matcher(content);

        while (toolUseMatcher.find()) {
            String toolBlock = toolUseMatcher.group(1);

            Pattern namePattern = Pattern.compile("<name>(.*?)</name>");
            Pattern argsPattern = Pattern.compile("<arguments>(.*?)</arguments>", Pattern.DOTALL);

            Matcher nameMatcher = namePattern.matcher(toolBlock);
            Matcher argsMatcher = argsPattern.matcher(toolBlock);

            String name = nameMatcher.find() ? nameMatcher.group(1).trim() : null;
            String arguments = argsMatcher.find() ? argsMatcher.group(1).trim() : null;

            if (name != null && arguments != null) {
                ToolCall toolCall = new ToolCall();
                Function function = new Function();
                function.setName(name);
                function.setArguments(arguments);
                toolCall.setFunction(function);
                toolCalls.add(toolCall);
            }
        }
        return toolCalls;
    }

    public static boolean parseExecutionDone(String content) {

        Pattern EXECUTION_PATTERN = Pattern.compile("<execution>(.*?)</execution>", Pattern.DOTALL);

        // 匹配 <is_done> 标签内的内容
        Pattern IS_DONE_PATTERN = Pattern.compile("<is_done>(.*?)</is_done>", Pattern.DOTALL);
        if (StringUtils.isBlank(content)) {
            return false;
        }

        // 先匹配 <execution> 标签内的内容
        Matcher executionMatcher = EXECUTION_PATTERN.matcher(content);
        if (!executionMatcher.find()) {
            return false;
        }

        // 获取 execution 标签内的内容
        String executionContent = executionMatcher.group(1);

        // 在 execution 内容中查找 is_done 标签
        Matcher isDoneMatcher = IS_DONE_PATTERN.matcher(executionContent);
        if (isDoneMatcher.find()) {
            String value = isDoneMatcher.group(1).trim().toLowerCase();
            if ("true".equals(value)) {
                return Boolean.TRUE;
            } else if ("false".equals(value)) {
                return Boolean.FALSE;
            }
        }

        return false;

    }

    public static Plan parsePlan(String content) throws JsonProcessingException {
        String xmlPart = extractOuterXmlTag(content, "plan");

        if (StringUtils.isNotBlank(xmlPart)) {
            XmlMapper mapper = new XmlMapper();
            return mapper.readValue(xmlPart, Plan.class);
        } else {
            return null;
        }
    }

    /**
     * 从任意文本中提取指定标签的最外层完整 XML 片段（支持嵌套）
     *
     * @param input   输入文本（可能包含非 XML 内容）
     * @param tagName 标签名，如 "plan"
     * @return 提取出的完整 XML 字符串，若未找到则返回 null
     */
    public static String extractOuterXmlTag(String input, String tagName) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        // 匹配开始标签：支持属性、空格、换行
        Pattern startPattern = Pattern.compile(
                "<\\s*" + Pattern.quote(tagName) + "(\\s+[^>]*)?>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher startMatcher = startPattern.matcher(input);

        int bestStart = -1;
        String bestMatch = null;
        int bestLength = -1;

        // 遍历所有可能的开始标签
        while (startMatcher.find()) {
            int startIdx = startMatcher.start();
            int pos = startIdx;
            int depth = 0;
            boolean found = false;

            // 手动匹配嵌套标签（避免正则无法处理递归）
            for (int i = 0; i < input.length(); i++) {
                if (i < pos) continue;

                if (input.startsWith("<" + tagName, i) ||
                        (input.charAt(i) == '<' &&
                                i + 1 + tagName.length() < input.length() &&
                                input.substring(i + 1, i + 1 + tagName.length()).toLowerCase().equals(tagName.toLowerCase()) &&
                                !Character.isLetterOrDigit(input.charAt(i + 1 + tagName.length())))) {

                    // 判断是否为开始标签（后面是空格或>）
                    char nextChar = input.charAt(i + tagName.length() + 1);
                    if (nextChar == ' ' || nextChar == '>' || nextChar == '\t' || nextChar == '\n' || nextChar == '\r') {
                        depth++;
                        i = i + tagName.length(); // 跳过标签名
                    }
                }
                else if (input.startsWith("</" + tagName, i)) {
                    depth--;
                    if (depth == 0) {
                        int endIdx = i + ("<" + tagName + ">").length(); // +2 for </, but we want to include >
                        String candidate = input.substring(startIdx, endIdx + 1);
                        if (candidate.length() > bestLength) {
                            bestStart = startIdx;
                            bestMatch = candidate;
                            bestLength = candidate.length();
                            found = true;
                        }
                        break;
                    }
                }
            }

            // 如果没找到闭合，继续找下一个开始标签
            if (!found) {
                continue;
            }
        }

        return bestMatch;
    }

    public static List<String> parseStepDescriptions(String xmlContent) throws JsonProcessingException {
        String xmlPart = extractOuterXmlTag(xmlContent, "steps");
        if (StringUtils.isNotBlank(xmlPart)) {
            XmlMapper mapper = new XmlMapper();
            return mapper.readValue(xmlPart, new TypeReference<List<Step>>() {})
                    .stream()
                    .map(Step::getDescription)
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    /**
     * extract the content and reasoning from OpenAI style response
     * @param llmResponse
     * @return
     */
    public static String parseOpenAIStyleResponse(String llmResponse) {
        String content = (String) JSONPath.eval(llmResponse, CONTENT_PATH);
        String reasoningContent = (String) JSONPath.eval(llmResponse, THOUGHT_PATH);

        return THINK_START + reasoningContent + THINK_END + StringEscapeUtils.unescapeJava(content);
    }
}
