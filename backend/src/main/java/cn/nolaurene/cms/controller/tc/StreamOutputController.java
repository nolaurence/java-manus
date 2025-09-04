package cn.nolaurene.cms.controller.tc;

import cn.nolaurene.cms.common.dto.tc.MessageRequest;
import cn.nolaurene.cms.common.dto.tc.StreamChatRequest;
import cn.nolaurene.cms.common.dto.tc.WhaleMessage;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author guofukang.gfk
 * @date 2025/3/21.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class StreamOutputController {

    private static final String MODEL = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B";

    private static final String CHAT_HOST = "https://api.siliconflow.cn";

    private static final String CHAT_ENDPOINT = "/v1/chat/completions";

    private static final String apiKey = "122222";

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            5,              // 核心线程数（固定大小）
            5,              // 最大线程数（与核心线程数相同）
            0L,             // 保持空闲线程的时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>() // 任务队列
    );


    @CrossOrigin
    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody StreamChatRequest request, HttpServletResponse httpServletResponse) {
        httpServletResponse.setContentType("text/event-stream");
        SseEmitter sseEmitter = new SseEmitter(60000L);
        executor.submit(() -> {
            try {
                question(request.getMessages(), sseEmitter, httpServletResponse);
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
        });
        return sseEmitter;
    }

    /**
     * 调用whale接口
     *
     * @param messages   用户询问的问题
     * @param sseEmitter 用于发送消息的sseEmitter
     */
    public void question(List<MessageRequest> messages, SseEmitter sseEmitter, HttpServletResponse httpServletResponse) {
        try {
            long startTimeMs = System.currentTimeMillis();

            // 组装请求参数
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("stream", true);
            List<JSONObject> whaleMessageList = messages.stream().map(message ->
                    new WhaleMessage(message.getRole(), message.getContent()).getJsonObject()).collect(Collectors.toList());
//            JSONObject message = WhaleMessage.ofUser(question);
            body.put("messages", whaleMessageList);

            // openapi协议请求
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(CHAT_HOST + CHAT_ENDPOINT);
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Authorization", "Bearer " + apiKey);

            StringEntity stringEntity = new StringEntity(body.toString(), "UTF-8");
            httpPost.setEntity(stringEntity);

            // 发送请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost);
                 InputStream inputStream = response.getEntity().getContent();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 512)) {

                log.info("建立链接时间：{}ms", System.currentTimeMillis() - startTimeMs);

                String line;
                long count = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }

                        // 接收到chunk后使用sseEmitter发送出去
                        sseEmitter.send(SseEmitter.event()
                                .data(data)
                                .id(String.valueOf(System.currentTimeMillis())));
                        httpServletResponse.getOutputStream().flush();
                        if (count == 0) {
                            log.info("首次发送耗时：{}ms", System.currentTimeMillis() - startTimeMs);
                        }
                        count++;
                    }
                }
                sseEmitter.complete();
                log.info("完成时间：{}ms", System.currentTimeMillis() - startTimeMs);
            } catch (IOException e) {
                sseEmitter.completeWithError(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
