package cn.nolaurene.cms.service.sandbox.backend.agent;


import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.llm.SiliconFlowClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * @author nolaurence
 * @date 2025/11/11 下午4:00
 * @description:
 */
@Service
public class AgentExecutorFactory {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ToolRegistry toolRegistry;

    public AgentExecutor createAgentExecutor(Agent agent) {
        // 创建专用的 LLM 客户端
        LlmClient llmClient = createLlmClient(agent.getLlmEndpoint(), agent.getLlmApiKey(), agent.getLlmModelName());

        // 使用 Spring 的 ApplicationContext 创建 AgentExecutor
        AgentExecutor executor = applicationContext.getBean(AgentExecutor.class);

        // 初始化 AgentExecutor
        executor.initialize(toolRegistry, llmClient, agent);

        return executor;
    }

    private LlmClient createLlmClient(String endpoint, String apiKey, String modelName) {
        return new SiliconFlowClient(endpoint, apiKey, modelName);
    }
}
