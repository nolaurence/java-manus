package cn.nolaurene.cms.service.sandbox.backend.sandbox;

import cn.nolaurene.cms.service.sandbox.backend.agent.AgentSession;
import cn.nolaurene.cms.service.sandbox.backend.session.GlobalAgentSessionManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SandboxUrlResolver {

    private final GlobalAgentSessionManager sessionManager;
    private final SandboxProperties properties;

    public SandboxUrlResolver(GlobalAgentSessionManager sessionManager, SandboxProperties properties) {
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    public String workerStreamUrl(String agentId) {
        SandboxLease lease = findLease(agentId);
        return StringUtils.defaultIfBlank(lease == null ? null : lease.getWorkerStreamUrl(), properties.getWorkerStreamUrl());
    }

    public String workerOpsUrl(String agentId) {
        SandboxLease lease = findLease(agentId);
        return StringUtils.defaultIfBlank(lease == null ? null : lease.getWorkerOpsUrl(), properties.getWorkerOpsUrl());
    }

    public String workerVncUrl(String agentId) {
        SandboxLease lease = findLease(agentId);
        return StringUtils.defaultIfBlank(lease == null ? null : lease.getWorkerVncUrl(), properties.getWorkerVncUrl());
    }

    private SandboxLease findLease(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return null;
        }
        AgentSession session = sessionManager.getSession(agentId);
        return session == null ? null : session.getSandboxLease();
    }
}
