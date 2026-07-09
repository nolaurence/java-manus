package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.service.sandbox.backend.sandbox.SandboxUrlResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/proxy/shell")
public class ShellManageController {

    private final SandboxUrlResolver sandboxUrlResolver;
    private final RestTemplate restTemplate = new RestTemplate();

    public ShellManageController(SandboxUrlResolver sandboxUrlResolver) {
        this.sandboxUrlResolver = sandboxUrlResolver;
    }

    // 2. Shell 执行
    @PostMapping("/shell/exec")
    public ResponseEntity<?> exec(@RequestParam String containerId, @RequestBody Map<String, String> command) {
//        String ip = workerManager.getWorkerIp(containerId);
//        String url = "http://" + ip + ":8080/worker/shell/exec";
        String url = sandboxUrlResolver.workerOpsUrl(containerId) + "/worker/shell/exec";
        return restTemplate.postForEntity(url, command, Object.class);
    }
}
