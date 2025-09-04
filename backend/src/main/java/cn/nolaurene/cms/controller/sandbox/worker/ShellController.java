package cn.nolaurene.cms.controller.sandbox.worker;

import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.service.sandbox.worker.shell.ShellWebSockerHandler;
import cn.nolaurene.cms.service.sandbox.worker.shell.ShellWebSocketConfig;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description:
 */
@Tag(name = "sandbox worker")
@RequestMapping("/worker/shell")
@RestController
public class ShellController {

    private static final String WORK_DIR = System.getProperty("user.dir");

    @GetMapping("/info")
    public Response<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "running");
        info.put("hostname", System.getenv("HOSTNAME"));
        info.put("workDir", WORK_DIR);
        info.put("timestamp", System.currentTimeMillis());
        return Response.success(info);
    }

    @PostMapping("/exec")
    public Response<Map<String, String>> exec(@RequestBody Map<String, String> request) {
        String command = request.get("command");
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(new File(WORK_DIR));
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            return Response.success(Map.of(
                    "success", String.valueOf(exitCode == 0),
                    "output", output.toString(),
                    "exitCode", String.valueOf(exitCode)
            ));
        } catch (Exception e) {
            return Response.success(Map.of("success", "false", "output", "", "error", e.getMessage()));
        }
    }
}
