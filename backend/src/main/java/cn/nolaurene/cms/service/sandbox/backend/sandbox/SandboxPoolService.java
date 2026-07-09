package cn.nolaurene.cms.service.sandbox.backend.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SandboxPoolService {

    private final SandboxProperties properties;
    private final Map<String, SandboxRuntime> sandboxes = new LinkedHashMap<>();
    private final Map<String, SandboxLease> leasesByAgentId = new ConcurrentHashMap<>();

    public SandboxPoolService(SandboxProperties properties) {
        this.properties = properties;
        reloadSandboxes();
    }

    public synchronized SandboxLease acquire(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            throw new IllegalArgumentException("Agent ID不能为空");
        }
        SandboxLease existingLease = leasesByAgentId.get(agentId);
        if (existingLease != null) {
            return existingLease;
        }
        ensureSandboxesLoaded();
        for (SandboxRuntime sandbox : sandboxes.values()) {
            if (sandbox.agentIds.size() < sandbox.maxSessions) {
                sandbox.agentIds.add(agentId);
                SandboxLease lease = SandboxLease.builder()
                        .sandboxId(sandbox.id)
                        .agentId(agentId)
                        .workerUrl(sandbox.workerUrl)
                        .workerMcpUrl(sandbox.workerMcpUrl)
                        .workerStreamUrl(sandbox.workerStreamUrl)
                        .workerOpsUrl(sandbox.workerOpsUrl)
                        .workerVncUrl(sandbox.workerVncUrl)
                        .leasedAt(Instant.now())
                        .build();
                leasesByAgentId.put(agentId, lease);
                log.info("申请沙箱成功: agentId={}, sandboxId={}, currentLeases={}/{}",
                        agentId, sandbox.id, sandbox.agentIds.size(), sandbox.maxSessions);
                return lease;
            }
        }
        log.warn("申请沙箱失败: agentId={}, totalSandboxes={}, activeLeases={}",
                agentId, sandboxes.size(), leasesByAgentId.size());
        throw new IllegalStateException("暂无可用沙箱，请稍后重试");
    }

    public synchronized void release(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return;
        }
        SandboxLease lease = leasesByAgentId.remove(agentId);
        if (lease == null) {
            return;
        }
        SandboxRuntime sandbox = sandboxes.get(lease.getSandboxId());
        if (sandbox != null) {
            sandbox.agentIds.remove(agentId);
            log.info("释放沙箱成功: agentId={}, sandboxId={}, currentLeases={}/{}",
                    agentId, sandbox.id, sandbox.agentIds.size(), sandbox.maxSessions);
        } else {
            log.info("释放沙箱成功: agentId={}, sandboxId={} (sandbox config not found)", agentId, lease.getSandboxId());
        }
    }

    public SandboxLease getLease(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return null;
        }
        return leasesByAgentId.get(agentId);
    }

    private void ensureSandboxesLoaded() {
        if (sandboxes.isEmpty()) {
            reloadSandboxes();
        }
    }

    private void reloadSandboxes() {
        sandboxes.clear();
        List<SandboxProperties.Instance> configured = properties.getInstances();
        if (configured == null || configured.isEmpty()) {
            configured = new ArrayList<>();
            SandboxProperties.Instance fallback = new SandboxProperties.Instance();
            fallback.setId("default");
            fallback.setWorkerUrl(properties.getWorkerUrl());
            fallback.setWorkerMcpUrl(properties.getWorkerMcpUrl());
            fallback.setWorkerStreamUrl(properties.getWorkerStreamUrl());
            fallback.setWorkerOpsUrl(properties.getWorkerOpsUrl());
            fallback.setWorkerVncUrl(properties.getWorkerVncUrl());
            fallback.setMaxSessions(1);
            configured.add(fallback);
        }
        for (SandboxProperties.Instance instance : configured) {
            SandboxRuntime runtime = SandboxRuntime.from(instance, properties);
            sandboxes.put(runtime.id, runtime);
        }
        log.info("沙箱池初始化完成: sandboxCount={}", sandboxes.size());
    }

    private static class SandboxRuntime {
        private String id;
        private String workerUrl;
        private String workerMcpUrl;
        private String workerStreamUrl;
        private String workerOpsUrl;
        private String workerVncUrl;
        private int maxSessions;
        private final List<String> agentIds = new ArrayList<>();

        private static SandboxRuntime from(SandboxProperties.Instance instance, SandboxProperties defaults) {
            SandboxRuntime runtime = new SandboxRuntime();
            runtime.id = StringUtils.defaultIfBlank(instance.getId(), instance.getWorkerUrl());
            runtime.workerUrl = StringUtils.defaultIfBlank(instance.getWorkerUrl(), defaults.getWorkerUrl());
            runtime.workerMcpUrl = StringUtils.defaultIfBlank(instance.getWorkerMcpUrl(), defaults.getWorkerMcpUrl());
            runtime.workerStreamUrl = StringUtils.defaultIfBlank(instance.getWorkerStreamUrl(), defaults.getWorkerStreamUrl());
            runtime.workerOpsUrl = StringUtils.defaultIfBlank(instance.getWorkerOpsUrl(), defaults.getWorkerOpsUrl());
            runtime.workerVncUrl = StringUtils.defaultIfBlank(instance.getWorkerVncUrl(), defaults.getWorkerVncUrl());
            runtime.maxSessions = instance.getMaxSessions() == null || instance.getMaxSessions() < 1 ? 1 : instance.getMaxSessions();
            return runtime;
        }
    }
}
