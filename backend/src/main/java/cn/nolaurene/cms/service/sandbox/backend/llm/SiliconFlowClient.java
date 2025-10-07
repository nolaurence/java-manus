package cn.nolaurene.cms.service.sandbox.backend.llm;

import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.llm.StreamResource;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class SiliconFlowClient implements LlmClient {

    private final String endpoint;
    private final String apiKey;
    private final String textChatPath = "/chat/completions";
    private static final String DEFAULT_MODEL = "Qwen3-Next-80B-A3B-Instruct-int4g-fp16-mixed";

    public SiliconFlowClient(String endpoint, String apiKey) {
        if (StringUtils.isBlank(endpoint) || StringUtils.isBlank(apiKey)) {
            throw new IllegalArgumentException("Endpoint and API key must not be blank");
        }
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            5,              // 核心线程数（固定大小）
            5,              // 最大线程数（与核心线程数相同）
            0L,             // 保持空闲线程的时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>() // 任务队列
    );

    public String chat(List<ChatMessage> messages) {
        long startTime = System.currentTimeMillis();

        JSONObject body = new JSONObject();
        body.put("model", DEFAULT_MODEL);
        body.put("stream", false);
//        body.put("max_tokens", 4096);
        body.put("enable_thinking", false);

        List<JSONObject> messageListToFire = messages.stream().map(message -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("role", message.getRole());
            jsonObject.put("content", message.getContent());
            return jsonObject;
        }).collect(Collectors.toList());
        body.put("messages", messageListToFire);

        log.info("[LLM client] url: {}; chat request body: {}", endpoint + textChatPath, body);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(endpoint + textChatPath);
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Authorization", "Bearer " + apiKey);
        httpPost.setEntity(new StringEntity(body.toString(), "UTF-8"));

        try {
            // TODO: siliconflow 返回其他状态码时，把body也解析出来
            CloseableHttpResponse response = httpClient.execute(httpPost);
            if (response.getStatusLine().getStatusCode() != 200) {
                log.error("Error response from server: {}", response.getStatusLine());
                return String.format("{\"choices\":[{\"message\":{\"content\":\"%s\"}}]}", "Error response from server: " + response.getStatusLine());
            }
            log.info("time costed: " + (System.currentTimeMillis() - startTime));
            // 读取响应内容
            return EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return "Error occurred while processing the request: " + e.getMessage();
        }

    }

    @Override
    public StreamResource streamChat(List<ChatMessage> messages, List<JSONObject> tools) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", DEFAULT_MODEL);
            body.put("stream", true);
            List<JSONObject> messageListToFire = messages.stream().map(message -> {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("role", message.getRole());
                jsonObject.put("content", message.getContent());
                return jsonObject;
            }).collect(Collectors.toList());
            body.put("messages", messageListToFire);
            body.put("tools", tools);  // support function calling

            log.info("[LLM client] streamChat request body: {}", JSON.toJSONString(body));
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(endpoint + textChatPath);
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Authorization", "Bearer " + apiKey);

            httpPost.setEntity(new StringEntity(body.toString(), "UTF-8"));

            CloseableHttpResponse response = httpClient.execute(httpPost);
            StreamResource streamResource = new StreamResource();
            streamResource.setResponse(response);
            streamResource.setInputStream(response.getEntity().getContent());

            return streamResource;
        } catch (Exception e) {
            return null;
        }
    }
}
