package cn.nolaurene.cms.service.sandbox.backend;


import dev.langchain4j.mcp.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class McpHeartbeatService {

    private final List<McpClient> mcpClients = new ArrayList<>();

    public void addClient(McpClient client) {
        if (client != null) {
            mcpClients.add(client);
            log.info("Adding client to mcp heartbeat service");
        } else {
            log.warn("Attempted to add a null McpClient");
        }
    }

    @Scheduled(fixedRate = 120000)  // every 2 minutes
    public void sendHeartbeat() {
        try {
            log.info("McpClient health check...");
            this.mcpClients.parallelStream().forEach(client -> {
                client.checkHealth();
                log.info("Sending heartbeat to server");
            });
        } catch (Exception e) {
            log.warn("Heartbeat failed, triggering reconnect");
        }
    }
}
