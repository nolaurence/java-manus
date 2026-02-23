package cn.nolaurene.cms.service.sandbox.backend;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class SseMessageForwardService {

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    public void forwardMessage(String targetIp, Integer targetPort, String agentId, String eventName, Object data) {
        String url = String.format("http://%s:%d/agents/%s/forward", targetIp, targetPort, agentId);

        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");

            ForwardRequest request = new ForwardRequest();
            request.setEventName(eventName);
            request.setData(data);

            StringEntity entity = new StringEntity(JSON.toJSONString(request), StandardCharsets.UTF_8);
            httpPost.setEntity(entity);

            CloseableHttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功转发消息到 {}: agentId={}, eventName={}", url, agentId, eventName);
            } else {
                log.warn("转发消息到 {} 失败: status code={}", url, statusCode);
            }
            EntityUtils.consume(response.getEntity());
        } catch (IOException e) {
            log.error("转发消息到 {} 失败: agentId={}, eventName={}", url, agentId, eventName, e);
        }
    }

    public static class ForwardRequest {
        private String eventName;
        private Object data;

        public String getEventName() {
            return eventName;
        }

        public void setEventName(String eventName) {
            this.eventName = eventName;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }
}
