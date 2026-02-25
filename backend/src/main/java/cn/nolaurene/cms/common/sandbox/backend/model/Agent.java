package cn.nolaurene.cms.common.sandbox.backend.model;


import cn.nolaurene.cms.service.sandbox.backend.agent.Executor;
import cn.nolaurene.cms.service.sandbox.backend.agent.Planner;
import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.McpToolProvider;
import lombok.Data;

import java.util.List;

/**
 * @author nolau
 * @date 2025/6/24
 * @description agent entity
 */
@Data
public class Agent {
    private String userId;

    private String agentId;

    private String status;

    private String message;

    private Planner planner;

    private Executor executor;

    private int maxLoop;

    private int executionMaxLoop;

    private String llmEndpoint;

    private String llmApiKey;

    private String llmModelName;

    private McpClient browserMcpClient;

    private McpClient nativeMcpClient;

    private McpToolProvider toolProvider;

    private List<ToolSpecification> toolSpecifications;

    private List<Tool> vanillaTools;
}
