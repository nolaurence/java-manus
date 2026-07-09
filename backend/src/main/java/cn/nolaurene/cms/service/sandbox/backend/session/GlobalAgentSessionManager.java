package cn.nolaurene.cms.service.sandbox.backend.session;

import cn.nolaurene.cms.service.sandbox.backend.agent.AgentSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GlobalAgentSessionManager {

    /**
     * 本地存储agent会话信息，存在即有创建，不存在就没有
     */
    private final ConcurrentHashMap<String, AgentSession> localSessions = new ConcurrentHashMap<>();

    public boolean createSession(String agentId, AgentSession session) {
        // 将新创建的agent存入本地会话
        return localSessions.putIfAbsent(agentId, session) == null;
    }

    public boolean removeSession(String agentId) {
        AgentSession removed = localSessions.remove(agentId);
        if (removed != null) {
            removed.releaseResources();
        }
        return true; // 成功删除会话
    }

    public AgentSession getSession(String agentId) {
        return localSessions.get(agentId);
    }

    public List<String> getAllSessionIds() {
        return new ArrayList<>(localSessions.keySet());
    }

    @PreDestroy
    public void releaseAllSessions() {
        new ArrayList<>(localSessions.keySet()).forEach(this::removeSession);
    }

    // TODO：分布式情况下存储session
}
