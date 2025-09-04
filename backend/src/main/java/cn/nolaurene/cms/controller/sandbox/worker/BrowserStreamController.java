package cn.nolaurene.cms.controller.sandbox.worker;

import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.service.sandbox.worker.browser.BrowserService;
import cn.nolaurene.cms.service.sandbox.worker.browser.FfmpegService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.Resource;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Date: 2025/6/10
 * Author: nolaurence
 * Description:
 */
@RestController
@RequestMapping("/worker/stream")
@Tag(name = "sandbox worker - browser stream")
public class BrowserStreamController {

    @Value("${sandbox.worker.logDir}")
    private String logDir;

    @Value("${sandbox.worker.stream-dir}")
    private String streamDir;

    @Resource
    FfmpegService ffmpegService;

    @Resource
    private BrowserService browserService;

    @Operation(summary = "get HLS master playlist")
    @GetMapping("/{streamId}.m3u8")
    public ResponseEntity<StreamingResponseBody> getHlsMaster(@PathVariable String streamId) {
        String streamPath = logDir + "/" + streamDir + "/" + streamId;
        String filePath = streamPath + "/output.m3u8";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(out -> {
                    try (InputStream in = new FileInputStream(filePath)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                });
    }

    @Operation(summary = "get HLS segment")
    @GetMapping("/{streamId}_{index}.ts")
    public ResponseEntity<StreamingResponseBody> getHlsSegment(@PathVariable String streamId, @PathVariable String index) {
        String streamPath = logDir + "/" + streamDir + "/" + streamId;
        String filePath = String.format("%s/%s_%03d.ts", streamPath, streamId, Integer.valueOf(index));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(out -> {
                    try (InputStream in = new FileInputStream(filePath)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                });
    }

    @Operation(summary = "start streaming")
    @GetMapping("/start/{streamId}")
    public Response<Boolean> startStream(@PathVariable String streamId) {
        try {
            ffmpegService.startStreaming(streamId);
            return Response.success(true);
        } catch (Exception e) {
            return Response.error("Failed to start streaming: " + e.getMessage(), false);
        }
    }

    @Operation(summary = "stop streaming")
    @GetMapping("/stop/{streamId}")
    public Response<Boolean> stopStream(@PathVariable String streamId) {
        try {
            ffmpegService.stopStreaming(streamId);
            return Response.success(true);
        } catch (Exception e) {
            return Response.error("Failed to stop streaming: " + e.getMessage(), false);
        }
    }

    @Operation(summary = "start chromium browser")
    @GetMapping("/start-browser")
    public Response<Boolean> startBrowser() {
        try {
            browserService.startBrowser();
            return Response.success(true);
        } catch (InterruptedException e) {
            return Response.error("Failed to start browser: " + e.getMessage(), false);
        }
    }

    @Operation(summary = "close chromium browser")
    @GetMapping("/close-browser")
    public Response<Boolean> closeBrowser() {
        try {
            browserService.closeBrowser();
            return Response.success(true);
        } catch (Exception e) {
            return Response.error("Failed to close browser: " + e.getMessage(), false);
        }
    }
}
