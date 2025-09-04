package cn.nolaurene.cms.controller.sandbox.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/proxy/shell")
public class ShellManageController {

    @Value("${sandbox.backend.worker-ops-url}")
    private String workerOpsUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 2. Shell 执行
    @PostMapping("/shell/exec")
    public ResponseEntity<?> exec(@RequestParam String containerId, @RequestBody Map<String, String> command) {
//        String ip = workerManager.getWorkerIp(containerId);
//        String url = "http://" + ip + ":8080/worker/shell/exec";
        String url = workerOpsUrl + "/worker/shell/exec";
        return restTemplate.postForEntity(url, command, Object.class);
    }
}
