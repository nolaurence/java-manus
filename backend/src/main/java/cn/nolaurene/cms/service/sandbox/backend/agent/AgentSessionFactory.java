package cn.nolaurene.cms.service.sandbox.backend.agent;


import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.McpHeartbeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * @author nolaurence
 * @date 2025/11/11 下午4:04
 * @description:
 */
@Service
public class AgentSessionFactory {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AgentExecutorFactory agentExecutorFactory;

    @Autowired
    private McpHeartbeatService mcpHeartbeatService;

    public AgentSession createAgentSession(Agent agent, String workerUrl, String sseEndpoint) {
        // 使用 Spring 的 ApplicationContext 创建 AgentSession
        AgentSession agentSession = applicationContext.getBean(AgentSession.class);

        // 初始化 AgentSession
        agentSession.initialize(agent, workerUrl, sseEndpoint, agentExecutorFactory, mcpHeartbeatService);

        return agentSession;
    }
}
