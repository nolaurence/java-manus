package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.McpHeartbeatService;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.llm.SiliconFlowClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * AgentSession 工厂服务
 * 负责创建和配置 AgentSession 实例
 * 
 * @author nolau
 * @date 2025/10/24
 */
@Service
public class AgentSessionFactory {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AgentExecutorFactory agentExecutorFactory;

    @Autowired
    private McpHeartbeatService mcpHeartbeatService;

    /**
     * 创建 AgentSession 实例
     * 
     * @param agent Agent 配置信息
     * @param workerUrl Worker 服务 URL
     * @param sseEndpoint SSE 端点
     * @return AgentSession 实例
     */
    public AgentSession createAgentSession(Agent agent, String workerUrl, String sseEndpoint) {
        // 使用 Spring 的 ApplicationContext 创建 AgentSession
        AgentSession agentSession = applicationContext.getBean(AgentSession.class);
        
        // 初始化 AgentSession
        agentSession.initialize(agent, workerUrl, sseEndpoint, agentExecutorFactory, mcpHeartbeatService);
        
        return agentSession;
    }
}