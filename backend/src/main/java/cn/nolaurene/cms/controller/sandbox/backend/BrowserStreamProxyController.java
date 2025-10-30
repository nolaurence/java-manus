package cn.nolaurene.cms.controller.sandbox.backend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * stream proxy controller
 * stream id is agent id
 */
@Slf4j
@RestController
@RequestMapping("/proxy/stream")
@Tag(name = "Stream Proxy to Worker", description = "代理转发到 Worker 的流媒体服务")
public class BrowserStreamProxyController {

    private final HttpClient httpClient = HttpClientBuilder.create().build();

    @Value("${sandbox.backend.worker-stream-url}")
    private String workerHost;

    // HLS M3U8 文件的 MIME 类型
    private static final String APPLICATION_X_MPEGURL = "application/vnd.apple.mpegurl";
//    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    @Operation(summary = "代理获取 HLS 主播放列表")
    @GetMapping("/{streamId}.m3u8")
    public ResponseEntity<StreamingResponseBody> proxyHlsMaster(@PathVariable String streamId) {

        String url = workerHost + "/worker/stream/" + streamId + ".m3u8";
        HttpGet httpGet = new HttpGet(url);

        String resultCode = "";
        try {
            HttpResponse response = httpClient.execute(httpGet);
            resultCode = String.valueOf(response.getStatusLine().getStatusCode());

            // 将response转换成ResponseEntity<StreamingResponseBody>
            if (response.getEntity() != null) {
                StreamingResponseBody responseBody = outputStream -> {
                    try (InputStream inputStream = response.getEntity().getContent()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                    }
                };

                // 设置正确的Content-Type
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(APPLICATION_X_MPEGURL));
                headers.setCacheControl("no-cache");

                return ResponseEntity.status(HttpStatus.valueOf(response.getStatusLine().getStatusCode()))
                        .headers(headers)
                        .body(responseBody);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (Exception e) {
            log.error("[BrowserStreamProxyController#proxyHlsMaster] meet error while proxying request: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "代理获取 HLS 视频片段")
    @GetMapping("/{streamId}_{index}.ts")
    public ResponseEntity<StreamingResponseBody> proxyHlsSegment(
            @PathVariable String streamId,
            @PathVariable String index) {
        String url = workerHost + "/worker/stream/" + streamId + "_" + index + ".ts";

        HttpGet httpGet = new HttpGet(url);
        String resultCode = "";
        try {
            HttpResponse response = httpClient.execute(httpGet);
            resultCode = String.valueOf(response.getStatusLine().getStatusCode());

            // 将response转换成ResponseEntity<StreamingResponseBody>
            if (response.getEntity() != null) {
                StreamingResponseBody responseBody = outputStream -> {
                    try (InputStream inputStream = response.getEntity().getContent()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                    }
                };

                // 设置正确的Content-Type
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setCacheControl("no-cache");

                return ResponseEntity.status(HttpStatus.valueOf(response.getStatusLine().getStatusCode()))
                        .headers(headers)
                        .body(responseBody);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (Exception e) {
            log.error("[BrowserStreamProxyController#proxyHlsSegment] meet error while proxying request: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "代理启动流媒体")
    @GetMapping("/start/{streamId}")
    public ResponseEntity<StreamingResponseBody> proxyStartStream(@PathVariable String streamId) {
        String url = workerHost + "/worker/stream/start/" + streamId;
        HttpGet httpGet = new HttpGet(url);

        String resultCode = "";
        try {
            HttpResponse response = httpClient.execute(httpGet);
            resultCode = String.valueOf(response.getStatusLine().getStatusCode());

            // 将response转换成ResponseEntity<StreamingResponseBody>
            if (response.getEntity() != null) {
                StreamingResponseBody responseBody = outputStream -> {
                    try (InputStream inputStream = response.getEntity().getContent()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                    }
                };

                // 设置正确的Content-Type
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setCacheControl("no-cache");

                return ResponseEntity.status(HttpStatus.valueOf(response.getStatusLine().getStatusCode()))
                        .headers(headers)
                        .body(responseBody);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (Exception e) {
            log.error("[BrowserStreamProxyController#proxyStartStream] meet error while proxying request: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "代理停止流媒体")
    @GetMapping("/stop/{streamId}")
    public ResponseEntity<StreamingResponseBody> proxyStopStream(@PathVariable String streamId) {
        String url = workerHost + "/worker/stream/stop/" + streamId;

        HttpGet httpGet = new HttpGet(url);
        String resultCode = "";

        try {
            HttpResponse response = httpClient.execute(httpGet);
            resultCode = String.valueOf(response.getStatusLine().getStatusCode());

            // 将response转换成ResponseEntity<StreamingResponseBody>
            if (response.getEntity() != null) {
                StreamingResponseBody responseBody = outputStream -> {
                    try (InputStream inputStream = response.getEntity().getContent()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                    }
                };

                // 设置正确的Content-Type
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setCacheControl("no-cache");

                return ResponseEntity.status(HttpStatus.valueOf(response.getStatusLine().getStatusCode()))
                        .headers(headers)
                        .body(responseBody);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (Exception e) {
            log.error("[BrowserStreamProxyController#proxyStopStream] meet error while proxying request: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
