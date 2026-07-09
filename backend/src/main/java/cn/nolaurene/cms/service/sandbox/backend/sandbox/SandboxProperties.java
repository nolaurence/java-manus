package cn.nolaurene.cms.service.sandbox.backend.sandbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "sandbox.backend")
public class SandboxProperties {

    private String workerUrl;
    private String workerMcpUrl;
    private String workerStreamUrl;
    private String workerOpsUrl;
    private String workerVncUrl;
    private String sseEndpoint;
    private List<Instance> instances = new ArrayList<>();

    @Data
    public static class Instance {
        private String id;
        private String workerUrl;
        private String workerMcpUrl;
        private String workerStreamUrl;
        private String workerOpsUrl;
        private String workerVncUrl;
        private Integer maxSessions = 1;
    }
}
