package cn.nolaurene.cms.service.sandbox.backend.sandbox;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SandboxLease {
    private String sandboxId;
    private String agentId;
    private String workerUrl;
    private String workerMcpUrl;
    private String workerStreamUrl;
    private String workerOpsUrl;
    private String workerVncUrl;
    private Instant leasedAt;
}
