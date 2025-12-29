package cn.nolaurene.cms.common.sandbox.backend.model;


import cn.nolaurene.cms.service.sandbox.backend.agent.Executor;
import cn.nolaurene.cms.service.sandbox.backend.agent.Planner;
import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
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

    private String llmEndpoint;

    private String llmApiKey;

    private String llmModelName;

    private McpSyncClient browserMcpClient;

    private McpSyncClient nativeMcpClient;

    private List<JSONObject> tools;

    private List<McpSchema.Tool> mcpTools;

    private String xmlToolsInfo;

    private List<Tool> vanillaTools;
}
