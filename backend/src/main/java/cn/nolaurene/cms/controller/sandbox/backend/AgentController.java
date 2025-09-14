package cn.nolaurene.cms.controller.sandbox.backend;


import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.AgentInfo;
import cn.nolaurene.cms.common.sandbox.backend.req.ChatRequest;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.service.sandbox.backend.agent.AgentSession;
import cn.nolaurene.cms.service.sandbox.backend.McpHeartbeatService;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.session.GlobalAgentSessionManager;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
@Slf4j
@RestController
@RequestMapping("/agents")
public class AgentController {

    private static final int MAX_RETRIES = 3;

    @Value("${sandbox.backend.max-loop}")
    private int maxLoop;

    @Value("${sandbox.backend.sse-timeout-ms}")
    private long sseTimeout;

    @Value("${sandbox.backend.max-threads}")
    private int maxThreads;

    @Value("${sandbox.backend.worker-url}")
    private String workerUrl;

    @Value("${sandbox.backend.sse-endpoint}")
    private String sseEndpoint;

    @Value("${llm-client.silicon-flow.endpoint}")
    private String siliconFlowEndpoint;

    @Value("${llm-client.silicon-flow.api-key}")
    private String siliconFlowApiKey;

    @Value("${server.port}")
    private String serverPort;

    @Resource
    private GlobalAgentSessionManager globalAgentSessionManager;

    @Resource
    private McpHeartbeatService mcpHeartbeatService;

    private ThreadPoolExecutor executor;

    @Resource
    private ConversationHistoryService conversationHistoryService;

    @PostConstruct
    public void initThreadPool() {
        executor = new ThreadPoolExecutor(
                maxThreads,              // 核心线程数（固定大小）
                maxThreads,              // 最大线程数（与核心线程数相同）
                0L,             // 保持空闲线程的时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>() // 任务队列
        );
    }

    /**
     * 创建Agent
     * @return AgentInfo
     */
    @PostMapping("/")
    public Response<AgentInfo> createAgent() {
        String agentId = UUID.randomUUID().toString().replace("-", "");

        // 重试三次
        for (int i = 0; i < MAX_RETRIES; i++) {
            Agent agent = new Agent();
            agent.setAgentId(agentId);
            agent.setMaxLoop(maxLoop);
            agent.setStatus("CREATED");
            agent.setMessage("Creating agent session...");
            agent.setLlmEndpoint(siliconFlowEndpoint);
            agent.setLlmApiKey(siliconFlowApiKey);

            // 请求worker /worker/mcp/start/${agentId} 创建mcpserver
//            boolean isMcpServerCreated = startMcpServer(agentId);
//            if (!isMcpServerCreated) {
//                log.error("Failed to start MCP server for agent: {}", agentId);
//                return Response.error("Failed to create mcp server", null);
//            }

            AgentSession agentSession = new AgentSession(agent, workerUrl, sseEndpoint);
            mcpHeartbeatService.addClient(agent.getMcpClient());

            try {
                boolean result = globalAgentSessionManager.createSession(agentId, agentSession);
                if (result) {
                    AgentInfo agentInfo = new AgentInfo();
                    agentInfo.setAgentId(agent.getAgentId());
                    agentInfo.setStatus(agent.getStatus());
                    agentInfo.setMessage(agent.getMessage());
                    return Response.success(agentInfo);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return Response.error("Failed to create agent after 3 attempts.", null);
    }

    @PostMapping("/{agentId}/chat")
    public SseEmitter chat(@PathVariable String agentId, @RequestBody ChatRequest request, HttpServletResponse httpServletResponse) {
        // 让浏览器知道这是一个SSE流
        SseEmitter sseEmitter = new SseEmitter(sseTimeout);
        httpServletResponse.setContentType("text/event-stream");

        executor.submit(() -> {
            AgentSession agentSession = globalAgentSessionManager.getSession(agentId);
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                log.error("创建AgentSession后休眠失败");
            }
            if (null == agentSession) {
                sseEmitter.completeWithError(new BusinessException("session not found for agentId: " + agentId));
            }
            try {
                assert agentSession != null;
                // configure persistence context for this chat
                try {
                    agentSession.setConversationPersistence(
                            conversationHistoryService,
                            request.getUserId() != null ? request.getUserId() : "anonymous",
                            (request.getSessionId() != null && !request.getSessionId().isEmpty()) ? request.getSessionId() : agentId
                    );
                } catch (Exception ignore) {}

                // persist user message if present
                try {
                    if (request.getMessage() != null && !request.getMessage().isEmpty()) {
                        ConversationRequest saveReq = new ConversationRequest();
                        saveReq.setUserId(request.getUserId() != null ? request.getUserId() : "anonymous");
                        saveReq.setSessionId(request.getSessionId() != null && !request.getSessionId().isEmpty() ? request.getSessionId() : agentId);
                        saveReq.setMessageType(ConversationHistoryDO.MessageType.USER);
                        saveReq.setContent(request.getMessage());
                        saveReq.setMetadata(null);
                        conversationHistoryService.saveConversation(saveReq);
                    }
                } catch (Exception ignore) {}
                agentSession.reactFlow(request.getMessage(), sseEmitter);
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
        });
        return sseEmitter;
    }

    private boolean startMcpServer(String agentId) {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        // in docker compose environment, the worker service is accessible via the hostname "worker"
        HttpGet httpGet = new HttpGet(workerUrl + "/worker/mcp/start/" + agentId);
        httpGet.addHeader("Host", "localhost:" + serverPort);

//        log.info("Starting MCP server for agentId: {}, URL: {}", agentId);
        try {
            // 执行HTTP请求
            CloseableHttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("MCP server started successfully for agent: {}", agentId);
                return true;
            } else {
                log.error("Failed to start MCP server for agent {}: HTTP status code {}", agentId, statusCode);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to start MCP server for agent {}: {}", agentId, e.getMessage(), e);
            return false;
        }
    }

    // ==================================== debug area ===============================================

    @GetMapping("/debug/toolCall")
    public Response<McpSchema.CallToolResult> toolCall() {
        if (StringUtils.isBlank(workerUrl) || (!workerUrl.startsWith("http://") && !workerUrl.startsWith("https://"))) {
            log.error("Invalid worker URL: {}", workerUrl);
            return null;
        }
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(workerUrl)
                .sseEndpoint(sseEndpoint)
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .capabilities(McpSchema.ClientCapabilities.builder()
//                        .roots(true)
                        .sampling()
                        .build())
                .build();
        client.initialize();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "https://bilibili.com");

        return Response.success(client.callTool(new McpSchema.CallToolRequest("browser_navigate", arguments)));
    }

    @GetMapping("/debug/toollist")
    public Response<McpSchema.ListToolsResult> listTool() {
        if (StringUtils.isBlank(workerUrl) || (!workerUrl.startsWith("http://") && !workerUrl.startsWith("https://"))) {
            log.error("Invalid worker URL: {}", workerUrl);
            return null;
        }
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(workerUrl)
                .sseEndpoint(sseEndpoint)
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .capabilities(McpSchema.ClientCapabilities.builder()
//                        .roots(true)
                        .sampling()
                        .build())
                .build();
        client.initialize();
        McpSchema.ListToolsResult listToolsResult = client.listTools();
        return Response.success(listToolsResult);
    }

    @GetMapping("/debug/start_mcp_server")
    public Response<String> startMcpServer() {
        if (StringUtils.isBlank(workerUrl) || (!workerUrl.startsWith("http://") && !workerUrl.startsWith("https://"))) {
            log.error("Invalid worker URL: {}", workerUrl);
            return null;
        }
        String fakeAgentId = UUID.randomUUID().toString().replace("-", "");
        startMcpServer(fakeAgentId);
        return Response.success(fakeAgentId);
    }
}
