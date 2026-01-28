package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.utils.ReActParser;
import com.alibaba.fastjson2.JSONPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.*;

@Slf4j
public class Executor {

    private static String EXECUTOR_PRIMARY_PROMPT;

    public Executor() {
        try {
            loadSystemPrompt();
        } catch (Exception e) {
            log.error("Failed to load executor system prompt", e);
            throw new RuntimeException("Failed to load executor system prompt", e);
        }
    }

    public String executeStep(LlmClient llmClient, List<Step> completedSteps, String goal, String step, String tools) throws IOException {

        // load executor prompt
        ClassPathResource resource = new ClassPathResource("prompts/execution.jinja");
        InputStream inputStream = resource.getInputStream();
        String executorPromptTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        // render the prompt
        Map<String, Object> context = new HashMap<>();
        context.put("availableTools", tools);
        context.put("goal", goal);
        context.put("step", step);
        context.put("completedSteps", renderStep(completedSteps));

        String executorPrompt = PromptRenderer.render(executorPromptTemplate, context);

        List<ChatMessage> messageList = new ArrayList<>();
        // add system prompt
        messageList.add(new ChatMessage(ChatMessage.Role.system, EXECUTOR_PRIMARY_PROMPT + "\n\n" + executorPrompt));

        // generate execution command
        String llmResponse = llmClient.chat(messageList);
        return ReActParser.parseOpenAIStyleResponse(llmResponse);
    }

    public String conclude(LlmClient llmClient, List<ChatMessage> memory) throws IOException {
        List<ChatMessage> history = removeSystemPrompt(memory);

        // load executor prompt
        ClassPathResource resource = new ClassPathResource("prompts/conclusion.jinja");
        InputStream inputStream = resource.getInputStream();
        String conclusionPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        history.add(new ChatMessage(ChatMessage.Role.user, conclusionPrompt));
        String llmResponse = llmClient.chat(history);
        return ReActParser.parseOpenAIStyleResponse(llmResponse);
    }

    private void loadSystemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/executionSystemPrompt.jinja");
        InputStream inputStream = resource.getInputStream();
        EXECUTOR_PRIMARY_PROMPT = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        String llmResponse = "{\"id\":\"01989e9ff0589e3e9947c8af2033f1f6\",\"object\":\"chat.completion\",\"created\":1755007938,\"model\":\"Qwen/Qwen3-8B\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"\\n\\nI can use the browser tool to navigate to the China Weather Network and check the weather forecast for Hangzhou.\\n\\u003ctool_use\\u003e\\n\\u003cname\\u003ebrowser_navigate\\u003c/name\\u003e\\n\\u003carguments\\u003e{\\\"url\\\": \\\"https://www.weather.com.cn\\\"}\\u003c/arguments\\u003e\\n\\u003c/tool_use\\u003e\",\"reasoning_content\":\"\\n好的，用户问的是杭州明天的天气怎么样。我需要获取并报告准确的天气预报信息。首先，应该使用浏览器工具访问权威的气象网站，比如中国天气网或者国际上的Weather.com、AccuWeather等。根据系统能力，可以访问互联网，所以需要先打开浏览器导航到这些网站之一。\\n\\n接下来，可能需要搜索“杭州”相关的天气信息，但根据搜索规则，应该分步骤搜索不同的属性，比如先找杭州明天的天气预报。不过用户可能希望直接得到结果，所以可能需要先导航到网站，然后查找具体信息。\\n\\n考虑到浏览器工具可以获取页面快照，可能需要先使用browser_snapshot来获取页面内容，然后通过解析来找到天气信息。或者直接在浏览器中输入搜索关键词，比如在搜索框中输入“杭州天气预报”，然后处理结果。\\n\\n不过根据工具列表，browser_snapshot可能需要先调用，但用户可能需要更直接的操作。或者使用browser_type在搜索框输入关键词，然后点击搜索按钮。但需要确保找到正确的页面，可能需要先导航到天气网的主页，再进行搜索。\\n\\n根据步骤，首先调用browser_navigate到中国天气网，然后查找杭州的天气信息。或者直接搜索“杭州天气”来获取相关页面，再处理数据。需要确保信息来源权威，所以应该优先选择中国天气网。\\n\\n可能还需要处理弹窗或者广告，所以可能需要使用browser_handle_dialog来处理任何出现的对话框，确保页面正常加载。如果PageSnapshot显示需要处理对话框，则先处理，再进行后续操作。\\n\\n然后，找到天气信息的具体部分，可能需要使用browser_click来点击“杭州”或者输入城市名称，但可能需要先定位到搜索框。或者直接在页面上输入城市名称，然后查找结果。\\n\\n如果页面上有多个城市选项，可能需要使用browser_select_option来选择正确的城市，或者通过其他方式定位到杭州的天气预报。不过可能需要先找到搜索框的位置，输入“杭州”，然后等待结果出现，再处理数据。\\n\\n可能需要使用browser_wait_for来等待特定文本出现，比如“杭州”的天气预报，或者等待一段时间。或者直接通过解析网络请求来获取数据，但根据工具限制，可能需要先通过浏览器访问页面，再截图或者解析。\\n\\n综上，首先导航到中国天气网，然后搜索杭州，处理可能的弹窗，找到天气信息部分，截取页面或者解析数据获取信息。如果页面内容不够，可能需要滚动查看，或者使用其他工具保存页面为PDF后处理。但根据工具限制，可能需要先使用browser_snapshot来获取页面内容，然后处理。\\n\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":4506,\"completion_tokens\":605,\"total_tokens\":5111,\"completion_tokens_details\":{\"reasoning_tokens\":549}},\"system_fingerprint\":\"\"}";
        String content = (String) JSONPath.eval(llmResponse, "$.choices[0].message.content");
        String reasoningContent = (String) JSONPath.eval(llmResponse, "$.choices[0].message.reasoning_content");

        String escapedResponse = StringEscapeUtils.unescapeJava(content);
        System.out.println("<think>\n" + reasoningContent + "\n</think>\n" + escapedResponse);
    }
}
