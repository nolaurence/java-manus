package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.llm.SiliconFlowClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * AgentExecutor 工厂服务
 * 负责创建和配置 AgentExecutor 实例
 * 
 * @author nolau
 * @date 2025/10/24
 */
@Service
public class AgentExecutorFactory {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ToolRegistry toolRegistry;

    /**
     * 创建 AgentExecutor 实例
     * 
     * @param agent Agent 配置信息
     * @return AgentExecutor 实例
     */
    public AgentExecutor createAgentExecutor(Agent agent) {
        // 创建专用的 LLM 客户端
        LlmClient llmClient = createLlmClient(agent.getLlmEndpoint(), agent.getLlmApiKey());
        
        // 使用 Spring 的 ApplicationContext 创建 AgentExecutor
        AgentExecutor executor = applicationContext.getBean(AgentExecutor.class);
        
        // 初始化 AgentExecutor
        executor.initialize(toolRegistry, llmClient, agent);
        
        return executor;
    }

    /**
     * 创建 LLM 客户端
     * 
     * @param endpoint LLM 服务端点
     * @param apiKey API 密钥
     * @return LlmClient 实例
     */
    private LlmClient createLlmClient(String endpoint, String apiKey) {
        return new SiliconFlowClient(endpoint, apiKey);
    }
}