package cn.nolaurene.cms.controller.sandbox.backend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * stream proxy controller
 * stream id is agent id
 */
@RestController
@RequestMapping("/proxy/stream")
@Tag(name = "Stream Proxy to Worker", description = "代理转发到 Worker 的流媒体服务")
public class BrowserStreamProxyController {

    @Resource
    private RestTemplate restTemplate;

    @Value("${sandbox.backend.worker-stream-url}")
    private String workerHost;

    // HLS M3U8 文件的 MIME 类型
    private static final String APPLICATION_X_MPEGURL = "application/vnd.apple.mpegurl";
//    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    @Operation(summary = "代理获取 HLS 主播放列表")
    @GetMapping("/{streamId}.m3u8")
    public void proxyHlsMaster(
            HttpServletResponse response,
            @PathVariable String streamId) throws IOException {

        String url = workerHost + "/worker/stream/" + streamId + ".m3u8";
        proxyStream(response, url, APPLICATION_X_MPEGURL);
    }

    @Operation(summary = "代理获取 HLS 视频片段")
    @GetMapping("/{streamId}_{index}.ts")
    public void proxyHlsSegment(
            HttpServletResponse response,
            @PathVariable String streamId,
            @PathVariable String index) throws IOException {

        String url = workerHost + "/worker/stream/" + streamId + "_" + index + ".ts";
        proxyStream(response, url, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @Operation(summary = "代理启动流媒体")
    @GetMapping("/start/{streamId}")
    public ResponseEntity<String> proxyStartStream(@PathVariable String streamId) {
        String url = workerHost + "/worker/stream/start/" + streamId;
        return forwardJsonRequest(url, HttpMethod.GET, null);
    }

    @Operation(summary = "代理停止流媒体")
    @GetMapping("/stop/{streamId}")
    public ResponseEntity<String> proxyStopStream(@PathVariable String streamId) {
        String url = workerHost + "/worker/stream/stop/" + streamId;
        return forwardJsonRequest(url, HttpMethod.GET, null);
    }

    /**
     * 代理流媒体文件
     */
    private void proxyStream(HttpServletResponse response, String url, String contentType) throws IOException {
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.ALL));

            HttpEntity<?> entity = new HttpEntity<>(headers);

            // 发送请求
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            // 设置响应头
            response.setContentType(contentType);
            response.setStatus(responseEntity.getStatusCodeValue());

            // 写入响应体
            if (responseEntity.getBody() != null) {
                response.getOutputStream().write(responseEntity.getBody());
            }

        } catch (Exception e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.getWriter().write("Error proxying stream: " + e.getMessage());
        }
    }

    /**
     * 代理 JSON 请求
     */
    private ResponseEntity<String> forwardJsonRequest(String url, HttpMethod method, Object requestBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);

            return restTemplate.exchange(url, method, entity, String.class);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Proxy failed: " + e.getMessage() + "\"}");
        }
    }
}
