package cn.nolaurene.cms.controller.sandbox.backend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * VNC 代理控制器
 * 提供 VNC 连接配置信息
 */
@Slf4j
@RestController
@RequestMapping("/api/vnc")
@Tag(name = "VNC Proxy", description = "VNC 代理服务")
public class VncProxyController {

    @Value("${sandbox.backend.worker-vnc-url}")
    private String workerVncUrl;

    @Operation(summary = "获取 VNC 配置信息")
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getVncConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("workerVncUrl", workerVncUrl);
        config.put("proxyEndpoint", "/vnc");
        config.put("status", "available");
        return ResponseEntity.ok(config);
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        return ResponseEntity.ok(result);
    }
}
