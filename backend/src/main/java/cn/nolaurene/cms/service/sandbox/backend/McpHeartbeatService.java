package cn.nolaurene.cms.service.sandbox.backend;


import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class McpHeartbeatService {

    private final List<McpSyncClient> mcpClients = new ArrayList<>();

//    public McpHeartbeatService(List<McpSyncClient> mcpClients) {
//        this.mcpClients = mcpClients;
//    }
    public void addClient(McpSyncClient client) {
        if (client != null) {
            mcpClients.add(client);
            log.info("Adding client to mcp heartbeat service");
        } else {
            log.warn("Attempted to add a null McpSyncClient");
        }
    }

    @Scheduled(fixedRate = 120000)  // every 2 minutes
    public void sendHeartbeat() {
        try {
            log.info("McpSyncClient ping...");
            this.mcpClients.parallelStream().forEach(client -> {
                client.ping();
                log.info("Sending heartbeat to server");
            });
        } catch (Exception e) {
            log.warn("Heartbeat failed, triggering reconnect");
        }
    }
}
