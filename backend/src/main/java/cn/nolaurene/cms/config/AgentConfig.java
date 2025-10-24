package cn.nolaurene.cms.config;

import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.llm.SiliconFlowClient;
import cn.nolaurene.cms.service.sandbox.backend.tool.CalculatorTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Agent 相关的 Spring 配置类
 * 
 * @author nolau
 * @date 2025/10/24
 */
@Configuration
public class AgentConfig {

    @Value("${llm-client.silicon-flow.endpoint}")
    private String siliconFlowEndpoint;

    @Value("${llm-client.silicon-flow.api-key}")
    private String siliconFlowApiKey;

    // ToolRegistry 已经通过 @Component 注解管理，不需要在这里重复定义

    /**
     * 创建 LlmClient Bean
     * 原型模式，每个 Agent 可以有独立的 LLM 客户端配置
     */
    @Bean
    @Scope("prototype")
    public LlmClient llmClient() {
        return new SiliconFlowClient(siliconFlowEndpoint, siliconFlowApiKey);
    }

    /**
     * 创建带自定义配置的 LlmClient
     * 
     * @param endpoint LLM 服务端点
     * @param apiKey API 密钥
     * @return LlmClient 实例
     */
    @Bean
    @Scope("prototype")
    public LlmClient customLlmClient(String endpoint, String apiKey) {
        return new SiliconFlowClient(endpoint, apiKey);
    }
}