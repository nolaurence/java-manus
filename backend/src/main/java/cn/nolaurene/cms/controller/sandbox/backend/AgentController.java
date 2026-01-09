package cn.nolaurene.cms.controller.sandbox.backend;


import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.AgentInfo;
import cn.nolaurene.cms.common.sandbox.backend.model.FileViewResponse;
import cn.nolaurene.cms.common.sandbox.backend.req.ChatRequest;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.dal.entity.LlmConfigDO;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.AgentSessionServerService;
import cn.nolaurene.cms.service.UserLoginService;
import cn.nolaurene.cms.service.LlmConfigService;
import cn.nolaurene.cms.service.sandbox.backend.agent.AgentSessionFactory;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.service.sandbox.backend.agent.AgentSession;
import cn.nolaurene.cms.service.sandbox.backend.McpHeartbeatService;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.SseMessageForwardService;
import cn.nolaurene.cms.service.sandbox.backend.session.GlobalAgentSessionManager;
import com.alibaba.fastjson2.JSON;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
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

    @Resource
    private AgentSessionFactory agentSessionFactory;

    private ThreadPoolExecutor executor;

    @Resource
    private ConversationHistoryService conversationHistoryService;

    @Resource
    private UserLoginService userLoginService;

    @Resource
    private LlmConfigService llmConfigService;

    @Resource
    private AgentSessionServerService agentSessionServerService;

    @Resource
    private SseMessageForwardService sseMessageForwardService;

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
    public Response<AgentInfo> createAgent(HttpServletRequest httpServletRequest) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        if (null == currentUserInfo) {
            return Response.error("未登录", null);
        }
        String agentId = UUID.randomUUID().toString().replace("-", "");

        // 从数据库获取用户自定义的LLM配置，如果没有则使用默认配置
        LlmConfigDO llmConfig = llmConfigService.getByUserId(currentUserInfo.getUserid());
        
        String endpoint = siliconFlowEndpoint;
        String apiKey = siliconFlowApiKey;
        String modelName = null;
        
        if (llmConfig != null && 
            StringUtils.isNotBlank(llmConfig.getEndpoint()) &&
            StringUtils.isNotBlank(llmConfig.getApiKey())) {
            endpoint = llmConfig.getEndpoint();
            apiKey = llmConfig.getApiKey();
            modelName = llmConfig.getModelName();
            log.info("Using custom LLM config from database for user {}: endpoint={}, modelName={}", 
                    currentUserInfo.getUserid(), endpoint, modelName);
        }

        // 重试三次
        for (int i = 0; i < MAX_RETRIES; i++) {
            Agent agent = new Agent();
            agent.setUserId(null != currentUserInfo ? currentUserInfo.getUserid().toString() : "anonymous");
            agent.setAgentId(agentId);
            agent.setMaxLoop(maxLoop);
            agent.setStatus("CREATED");
            agent.setMessage("Creating agent session...");
            agent.setLlmEndpoint(endpoint);
            agent.setLlmApiKey(apiKey);
            agent.setLlmModelName(modelName);

            AgentSession agentSession = agentSessionFactory.createAgentSession(agent, workerUrl, sseEndpoint);

            try {
                boolean result = globalAgentSessionManager.createSession(agentId, agentSession);
                if (result) {
                    String currentIp = agentSessionServerService.getCurrentServerIp();
                    agentSessionServerService.saveOrUpdate(agentId, currentIp, Integer.valueOf(serverPort));

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
    public SseEmitter chat(@PathVariable String agentId, @RequestBody ChatRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        if (null == currentUserInfo) {
            throw new BusinessException("未登录", null);
        }
        String userId = currentUserInfo.getUserid().toString();

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
                return;
            }

            // add userId in agentSession
            agentSession.getAgent().setUserId(userId);

            try {
                String currentIp = agentSessionServerService.getCurrentServerIp();
                agentSessionServerService.saveOrUpdate(agentId, currentIp, Integer.valueOf(serverPort));
            } catch (Exception e) {
                log.error("记录Agent Session Server信息失败", e);
            }

            try {
                // configure persistence context for this chat
                try {
                    agentSession.setConversationPersistence(
                            conversationHistoryService,
                            request.getUserId() != null ? request.getUserId() : "anonymous",
                            (request.getSessionId() != null && !request.getSessionId().isEmpty()) ? request.getSessionId() : agentId
                    );
                } catch (Exception ignore) {}

                // persist user message if present
                agentSession.reactFlow(request.getMessage(), sseEmitter);
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
        });
        return sseEmitter;
    }

    @PostMapping("/{agentId}/forward")
    public Response<String> forwardMessage(@PathVariable String agentId, @RequestBody SseMessageForwardService.ForwardRequest request) {
        AgentSession agentSession = globalAgentSessionManager.getSession(agentId);
        if (agentSession == null) {
            log.warn("收到转发消息，但session不存在: agentId={}", agentId);
            return Response.error("Session not found", null);
        }

        log.info("收到转发的SSE消息: agentId={}, eventName={}", agentId, request.getEventName());

        agentSession.sendMessage(request.getEventName(), request.getData());

        return Response.success("Message forwarded successfully");
    }

    /**
     * view file content
     * @param agentId
     * @param request
     * @return
     */
    @PostMapping("/{agentId}/file")
    public Response<FileViewResponse> viewFile(@PathVariable String agentId, @RequestBody Map<String, String> request) {
        try {
            String file = request.get("file");
            if (StringUtils.isBlank(file)) {
                return Response.error("File path is required", null);
            }

            // Get the agent session
            AgentSession agentSession = globalAgentSessionManager.getSession(agentId);
            if (agentSession == null) {
                return Response.error("Agent session not found", null);
            }

            // Get the native MCP client from agent
            McpSyncClient nativeMcpClient = agentSession.getAgent().getNativeMcpClient();
            if (nativeMcpClient == null) {
                return Response.error("Native MCP client not initialized", null);
            }

            // Prepare arguments for file_read tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("file", file);

            // Call the file_read tool
            McpSchema.CallToolResult callToolResult = nativeMcpClient.callTool(
                    new McpSchema.CallToolRequest("file_read", arguments)
            );

            // Check for errors
            if (callToolResult == null || (callToolResult.getIsError() != null && callToolResult.getIsError())) {
                String errorMsg = "Failed to read file";
                if (callToolResult != null && callToolResult.getContent() != null) {
                    errorMsg += ": " + JSON.toJSONString(callToolResult.getContent());
                }
                return Response.error(errorMsg, null);
            }

            // Extract content from the result
            String content = "";
            if (callToolResult.getContent() != null && !callToolResult.getContent().isEmpty()) {
                // The content is typically a list of TextContent objects
                List<McpSchema.Content> contentList = callToolResult.getContent();
                StringBuilder sb = new StringBuilder();
                for (McpSchema.Content c : contentList) {
                    if (c instanceof McpSchema.TextContent) {
                        sb.append(((McpSchema.TextContent) c).getText());
                    }
                }
                content = sb.toString();
            }

            // Build response
            FileViewResponse response = new FileViewResponse();
            response.setFile(file);
            response.setContent(content);

            return Response.success(response);
        } catch (Exception e) {
            log.error("Error viewing file for agent: {}", agentId, e);
            return Response.error("Error viewing file: " + e.getMessage(), null);
        }
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
