package cn.nolaurene.cms.service.sandbox.backend.utils;


import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.llm.SiliconFlowClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author nolaurence
 * @date 2026/1/9 下午12:04
 * @description:
 */
@Slf4j
public class LLMJsonParser {

    public enum ParseStrategy {
        DIRECT("direct"),
        MARKDOWN_BLOCK("markdown_block"),
        REGEX_EXTRACT("regex_extract"),
        CLEANUP_AND_PARSE("cleanup_and_parse"),
        LLM_EXTRACT_AND_FIX("llm_extract_and_fix");

        private final String value;

        ParseStrategy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private final LlmClient llm;
    private final ObjectMapper objectMapper; // Used for JSON operations
    private final List<Function<String, Optional<Object>>> strategies;

    public LLMJsonParser(Agent agent) {
        this.llm = new SiliconFlowClient(agent.getLlmEndpoint(), agent.getLlmApiKey(), agent.getLlmModelName());
        this.objectMapper = new ObjectMapper(); // Consider configuring this globally as a bean
        this.strategies = Arrays.asList(
                this::_tryDirectParse,
                this::_tryMarkdownBlockParse,
                // this::_tryRegexExtract, // Commented out as in Python code
                this::_tryCleanupAndParse,
                this::_tryLlmExtractAndFix
        );
    }
    
    public Object parse(String text, Object defaultValue) {
        log.info("Parsing text: {}", text);
        if (text == null || text.trim().isEmpty()) {
            if (defaultValue != null) {
                return defaultValue;
            }
            throw new IllegalArgumentException("Empty input string");
        }

        String cleanedOutput = text.trim();

        for (Function<String, Optional<Object>> strategy : strategies) {
            try {
                Optional<Object> result = strategy.apply(cleanedOutput);
                if (result.isPresent()) {
                    log.info("Successfully parsed using strategy: {}", strategy.getClass().getSimpleName());
                    return result.get();
                }
            } catch (Exception e) {
                log.warn("Strategy {} failed: {}", strategy.getClass().getSimpleName(), e.getMessage(), e);
                // Continue to the next strategy
            }
        }

        if (defaultValue != null) {
            log.warn("All parsing strategies failed, returning default value");
            return defaultValue;
        }

        throw new RuntimeException("Failed to parse JSON from LLM output: " + text.substring(0, Math.min(text.length(), 1000)) + "...");
    }

    private Optional<Object> _tryDirectParse(String text) {
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return Optional.of(parsed);
        } catch (JsonProcessingException e) {
            log.debug("Direct parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Object> _tryMarkdownBlockParse(String text) {
        String[] patterns = {
                "(?s)```json\\s*\\n(.*?)\\n```", // (?s) enables DOTALL mode
                "(?s)```\\s*\\n(.*?)\\n```",
                "`([^`]*)`"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group(1); // Group 1 captures the content inside the block
                if (match != null) {
                    try {
                        Object parsed = objectMapper.readValue(match.trim(), Object.class);
                        return Optional.of(parsed);
                    } catch (JsonProcessingException e) {
                        log.debug("Markdown block parse failed for match: {}", match);
                        // Continue to next match
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Object> _tryRegexExtract(String text) {
        String[] jsonPatterns = {
                "\\{.*\\}",  // Object
                "\\[.*\\]"   // Array
        };

        for (String patternStr : jsonPatterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group();
                try {
                    Object parsed = objectMapper.readValue(match, Object.class);
                    return Optional.of(parsed);
                } catch (JsonProcessingException e) {
                    log.debug("Regex extract parse failed for match: {}", match);
                    // Continue to next match
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Object> _tryCleanupAndParse(String text) {
        String[] prefixes = {"json:", "result:", "output:", "response:"};
        String[] suffixes = {".", "..."};

        String cleaned = text;

        for (String prefix : prefixes) {
            if (cleaned.toLowerCase().startsWith(prefix.toLowerCase())) {
                cleaned = cleaned.substring(prefix.length()).trim();
                break; // Only remove the first matching prefix
            }
        }

        for (String suffix : suffixes) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
            }
        }

        cleaned = _fixJsonFormatting(cleaned);

        try {
            Object parsed = objectMapper.readValue(cleaned, Object.class);
            return Optional.of(parsed);
        } catch (JsonProcessingException e) {
            log.debug("Cleanup and parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Object> _tryLlmExtractAndFix(String text) {
        try {
            Object result = _llmExtractAndFixSync(text);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("LLM extract and fix failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Object _llmExtractAndFixSync(String text) {
        String prompt = String.format(PROMPT_TEMPLATE, text);

        String responseString = llm.chat(Collections.singletonList(new ChatMessage(ChatMessage.Role.user, prompt)));
        Map<String, Object> response = JSON.parseObject(responseString, new TypeReference<Map<String, Object>>() {
        });
        String content = (String) response.get("content");
        if (content != null) {
            content = content.trim();
            if (!content.isEmpty() && !content.equals("null")) {
                try {
                    return objectMapper.readValue(content, Object.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse LLM returned content as JSON: {}", e.getMessage(), e);
                    return null;
                }
            }
        }
        return null;
    }

    private String _fixJsonFormatting(String text) {
        // Fix trailing commas
        text = text.replaceAll(",(\\s*[}\\]])", "$1");

        // Fix missing quotes around keys (basic attempt)
        text = text.replaceAll("(\\w+)\\s*:", "\"$1\":");

        // Fix unescaped double quotes in string values (simplified example)
        // This is a very basic approach and might not cover all edge cases
        // Example: Replace 'key': 'value' style to "key": "value"
        text = text.replaceAll("'([^']*)'\\s*:\\s*'([^']*)'", "\"$1\":\"$2\"");
        text = text.replaceAll("'([^']*)'\\s*:\\s*(\\d+)", "\"$1\":$2"); // key: number
        text = text.replaceAll("'([^']*)'\\s*:\\s*(true|false|null)", "\"$1\":$2"); // key: boolean/null

        return text;
    }

    private static final String PROMPT_TEMPLATE =
            "Please extract and fix the JSON from the following text. Return only valid JSON without any explanation or markdown formatting.\n\n" +
                    "Input text:\n%s\n\n" +
                    "Requirements:\n" +
                    "1. Extract any JSON-like content from the text\n" +
                    "2. Fix common JSON formatting issues (missing quotes, trailing commas, etc.)\n" +
                    "3. Return only the valid JSON, no additional text\n" +
                    "4. If multiple JSON objects exist, return the most complete one\n" +
                    "5. If no valid JSON can be extracted or fixed, return null\n\n" +
                    "JSON:";

    // Example usage
    public static void main(String[] args) {
//        LlmClient mockLlm = new SiliconFlowClient(); // Assuming you have a real implementation
//        LLMJsonParser parser = new LLMJsonParser(mockLlm);

        String input = "{ \"name\": \"John\", \"age\": 30 }";
        // String input = "Here is some text:\n```json\n{ \"status\": \"success\", \"data\": [] }\n```\nEnd.";
        // String input = "Invalid: { name: 'John' }";

//        Object result = parser.parse(input, "default");

//        System.out.println("Parsed Result: " + result);
//        System.out.println("Type: " + (result != null ? result.getClass().getName() : "null"));
    }
}
