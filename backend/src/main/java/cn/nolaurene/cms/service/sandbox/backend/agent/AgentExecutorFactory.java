package cn.nolaurene.cms.service.sandbox.backend.agent;


import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
        ChatModel chatModel = createChatModel(
                agent.getLlmEndpoint(), agent.getLlmApiKey(), agent.getLlmModelName());

        AgentExecutor executor = applicationContext.getBean(AgentExecutor.class);

        executor.initialize(toolRegistry, chatModel, agent);

        return executor;
    }

    private ChatModel createChatModel(String endpoint, String apiKey, String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
