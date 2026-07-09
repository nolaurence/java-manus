package cn.nolaurene.cms.controller.sandbox.backend;


import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.AgentInfo;
import cn.nolaurene.cms.common.sandbox.backend.model.FileViewResponse;
import cn.nolaurene.cms.common.sandbox.backend.model.ShellViewResponse;
import cn.nolaurene.cms.common.sandbox.backend.req.ChatRequest;
import cn.nolaurene.cms.common.dto.ConversationResponse;
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
import cn.nolaurene.cms.service.sandbox.backend.SseMessageForwardService;
import cn.nolaurene.cms.service.sandbox.backend.session.GlobalAgentSessionManager;
import cn.nolaurene.cms.service.sandbox.backend.sandbox.SandboxLease;
import cn.nolaurene.cms.service.sandbox.backend.sandbox.SandboxPoolService;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final long MCP_RETRY_BASE_DELAY_MS = 200L;

    @Value("${sandbox.backend.max-loop}")
    private int maxLoop;

    @Value("${sandbox.backend.max-execution-loop}")
    private int maxExecutionLoop;

    @Value("${sandbox.backend.sse-timeout-ms}")
    private long sseTimeout;

    @Value("${sandbox.backend.heartbeat-interval-ms:30000}")
    private long heartbeatInterval;

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

    @Resource
    private SandboxPoolService sandboxPoolService;

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
        Exception lastCreateException = null;
        for (int i = 0; i < MAX_RETRIES; i++) {
            Agent agent = new Agent();
            agent.setUserId(null != currentUserInfo ? currentUserInfo.getUserid().toString() : "anonymous");
            agent.setAgentId(agentId);
            agent.setMaxLoop(maxLoop);
            agent.setExecutionMaxLoop(maxExecutionLoop);
            agent.setStatus("CREATED");
            agent.setMessage("Creating agent session...");
            agent.setLlmEndpoint(endpoint);
            agent.setLlmApiKey(apiKey);
            agent.setLlmModelName(modelName);

            SandboxLease sandboxLease = null;
            try {
                sandboxLease = sandboxPoolService.acquire(agentId);
                AgentSession agentSession = agentSessionFactory.createAgentSession(agent, sandboxLease, sseEndpoint);
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
                agentSession.releaseResources();
            } catch (Exception e) {
                lastCreateException = e;
                if (sandboxLease != null) {
                    sandboxPoolService.release(agentId);
                }
                log.error("创建Agent失败: agentId={}, attempt={}/{}", agentId, i + 1, MAX_RETRIES, e);
            }
        }

        String errorMessage = lastCreateException == null ? "Failed to create agent after 3 attempts." : lastCreateException.getMessage();
        return Response.error(errorMessage, null);
    }

    @DeleteMapping("/{agentId}")
    public Response<Boolean> releaseAgent(@PathVariable String agentId, HttpServletRequest httpServletRequest) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        if (null == currentUserInfo) {
            return Response.error("未登录", false);
        }
        globalAgentSessionManager.removeSession(agentId);
        agentSessionServerService.deleteByAgentId(agentId);
        return Response.success(true);
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
        httpServletResponse.setHeader("Cache-Control", "no-cache");
        httpServletResponse.setHeader("Connection", "keep-alive");

        // 创建心跳机制
        ScheduledExecutorService heartbeatExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r);
            t.setName("sse-heartbeat-" + agentId);
            t.setDaemon(true);
            return t;
        });
        AtomicBoolean isActive = new AtomicBoolean(true);

        // 定期发送心跳
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (isActive.get()) {
                try {
                    sseEmitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data("{\"type\":\"ping\",\"timestamp\":" + System.currentTimeMillis() + "}"));
                    log.debug("SSE heartbeat sent for agent: {}", agentId);
                } catch (Exception e) {
                    log.warn("SSE heartbeat failed for agent: {}, connection may be closed", agentId);
                    isActive.set(false);
                    heartbeatExecutor.shutdown();
                }
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);

        // 连接关闭时清理心跳
        sseEmitter.onCompletion(() -> {
            log.info("SSE connection completed for agent: {}", agentId);
            isActive.set(false);
            heartbeatExecutor.shutdown();
        });

        sseEmitter.onTimeout(() -> {
            log.warn("SSE connection timeout for agent: {}", agentId);
            isActive.set(false);
            heartbeatExecutor.shutdown();
        });

        sseEmitter.onError((e) -> {
            log.error("SSE connection error for agent: {}", agentId, e);
            isActive.set(false);
            heartbeatExecutor.shutdown();
        });

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
                assert agentSession != null;
                // configure persistence context for this chat
//                try {
////                    agentSession
//                }
                String currentIp = agentSessionServerService.getCurrentServerIp();
                agentSessionServerService.saveOrUpdate(agentId, currentIp, Integer.valueOf(serverPort));
            } catch (Exception e) {
                log.error("记录Agent Session Server信息失败", e);
            }

            try {
                // persist user message if present
                agentSession.reactFlow(request.getMessage(), Boolean.TRUE.equals(request.getPlanMode()), sseEmitter);
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
        });
        return sseEmitter;
    }

    @PostMapping("/{agentId}/resume")
    public SseEmitter resume(@PathVariable String agentId,
                             @RequestParam(value = "afterId", required = false) Long afterId,
                             HttpServletRequest httpServletRequest,
                             HttpServletResponse httpServletResponse) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        if (null == currentUserInfo) {
            throw new BusinessException("未登录", null);
        }
        String userId = currentUserInfo.getUserid().toString();

        SseEmitter sseEmitter = new SseEmitter(sseTimeout);
        httpServletResponse.setContentType("text/event-stream");
        httpServletResponse.setHeader("Cache-Control", "no-cache");
        httpServletResponse.setHeader("Connection", "keep-alive");

        ScheduledExecutorService heartbeatExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r);
            t.setName("sse-resume-heartbeat-" + agentId);
            t.setDaemon(true);
            return t;
        });
        AtomicBoolean isActive = new AtomicBoolean(true);

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (isActive.get()) {
                try {
                    sseEmitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data("{\"type\":\"ping\",\"timestamp\":" + System.currentTimeMillis() + "}"));
                    log.debug("SSE resume heartbeat sent for agent: {}", agentId);
                } catch (Exception e) {
                    log.warn("SSE resume heartbeat failed for agent: {}, connection may be closed", agentId);
                    isActive.set(false);
                    heartbeatExecutor.shutdown();
                }
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);

        sseEmitter.onCompletion(() -> {
            log.info("SSE resume connection completed for agent: {}", agentId);
            isActive.set(false);
            heartbeatExecutor.shutdown();
        });

        sseEmitter.onTimeout(() -> {
            log.warn("SSE resume connection timeout for agent: {}", agentId);
            isActive.set(false);
            heartbeatExecutor.shutdown();
        });

        sseEmitter.onError((e) -> {
            log.error("SSE resume connection error for agent: {}", agentId, e);
            isActive.set(false);
            heartbeatExecutor.shutdown();
        });

        executor.submit(() -> {
            AgentSession agentSession = globalAgentSessionManager.getSession(agentId);
            if (null == agentSession) {
                sseEmitter.completeWithError(new BusinessException("session not found for agentId: " + agentId));
                return;
            }

            agentSession.getAgent().setUserId(userId);
            try {
                String currentIp = agentSessionServerService.getCurrentServerIp();
                agentSessionServerService.saveOrUpdate(agentId, currentIp, Integer.valueOf(serverPort));
            } catch (Exception e) {
                log.error("记录Agent Session Server信息失败", e);
            }

            agentSession.resumeFlow(sseEmitter);
            replayMissedEvents(agentId, afterId, sseEmitter);
        });

        return sseEmitter;
    }

    private void replayMissedEvents(String agentId, Long afterId, SseEmitter sseEmitter) {
        if (afterId == null || afterId <= 0) {
            return;
        }
        try {
            List<ConversationResponse> missedEvents = conversationHistoryService.getSessionConversationsAfterId(agentId, afterId);
            for (ConversationResponse event : missedEvents) {
                if (event.getMessageType() != ConversationHistoryDO.MessageType.ASSISTANT || event.getEventType() == null) {
                    continue;
                }
                sseEmitter.send(SseEmitter.event()
                        .name(event.getEventType().getType())
                        .data(event.getContent())
                        .id(String.valueOf(event.getId())));
            }
            log.info("SSE replay completed: agentId={}, afterId={}, count={}", agentId, afterId, missedEvents.size());
        } catch (Exception e) {
            log.warn("SSE replay failed: agentId={}, afterId={}", agentId, afterId, e);
        }
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
            McpClient nativeMcpClient = agentSession.getAgent().getNativeMcpClient();
            if (nativeMcpClient == null) {
                return Response.error("Native MCP client not initialized", null);
            }

            // Prepare arguments for file_read tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("file", file);

            // Call the file_read tool via langchain4j MCP client
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .name("file_read")
                    .arguments(JSON.toJSONString(arguments))
                    .build();
            ToolExecutionResult toolResult = executeMcpToolWithRetry(nativeMcpClient, toolRequest, agentId, "file_read");

            // Check for errors
            if (toolResult.isError()) {
                return Response.error("Failed to read file: " + toolResult.resultText(), null);
            }

            // Extract content from the result
            String content = toolResult.resultText();

            // Build response
            FileViewResponse response = new FileViewResponse();
            response.setFile(file);
            response.setContent(content);

            return Response.success(response);
        } catch (Exception e) {
            if (isTransientMcpTransportException(e)) {
                log.warn("Transient MCP transport error viewing file for agent {}: {}", agentId, e.getMessage());
            } else {
                log.error("Error viewing file for agent: {}", agentId, e);
            }
            return Response.error("Error viewing file: " + e.getMessage(), null);
        }
    }

    /**
     * View shell session content
     * @param agentId Agent ID
     * @param request contains sessionId
     * @return Shell session output with console records
     */
    @PostMapping("/{agentId}/shell")
    public Response<ShellViewResponse> viewShell(@PathVariable String agentId, @RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            if (StringUtils.isBlank(sessionId)) {
                return Response.error("Session ID is required", null);
            }

            // Get the agent session
            AgentSession agentSession = globalAgentSessionManager.getSession(agentId);
            if (agentSession == null) {
                return Response.error("Agent session not found", null);
            }

            // Get the native MCP client from agent
            McpClient nativeMcpClient = agentSession.getAgent().getNativeMcpClient();
            if (nativeMcpClient == null) {
                return Response.error("Native MCP client not initialized", null);
            }

            // Prepare arguments for shell_view tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("id", sessionId);

            // Call the shell_view tool via langchain4j MCP client
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .name("shell_view")
                    .arguments(JSON.toJSONString(arguments))
                    .build();
            ToolExecutionResult toolResult = executeMcpToolWithRetry(nativeMcpClient, toolRequest, agentId, "shell_view");

            // Check for errors
            if (toolResult.isError()) {
                return Response.error("Failed to view shell session: " + toolResult.resultText(), null);
            }

            // Parse the JSON result from shell_view tool
            String resultText = toolResult.resultText();
            ShellViewResponse response = JSON.parseObject(resultText, ShellViewResponse.class);
            if (response == null) {
                response = new ShellViewResponse();
                response.setOutput(resultText);
                response.setSessionId(sessionId);
            }

            return Response.success(response);
        } catch (Exception e) {
            if (isTransientMcpTransportException(e)) {
                log.warn("Transient MCP transport error viewing shell session for agent {}: {}", agentId, e.getMessage());
            } else {
                log.error("Error viewing shell session for agent: {}", agentId, e);
            }
            return Response.error("Error viewing shell session: " + e.getMessage(), null);
        }
    }

    private ToolExecutionResult executeMcpToolWithRetry(
            McpClient mcpClient,
            ToolExecutionRequest request,
            String agentId,
            String toolName
    ) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return mcpClient.executeTool(request);
            } catch (Exception e) {
                lastException = e;
                boolean shouldRetry = isTransientMcpTransportException(e) && attempt < MAX_RETRIES;
                if (!shouldRetry) {
                    throw e;
                }

                log.warn("Transient MCP transport error calling {} for agent {}, retry {}/{}: {}",
                        toolName, agentId, attempt + 1, MAX_RETRIES, e.getMessage());
                sleepBeforeMcpRetry(attempt);
            }
        }

        throw lastException != null ? lastException : new IllegalStateException("Unknown MCP tool execution failure");
    }

    private void sleepBeforeMcpRetry(int attempt) throws InterruptedException {
        try {
            Thread.sleep(MCP_RETRY_BASE_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private boolean isTransientMcpTransportException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (StringUtils.containsIgnoreCase(className, "IOException")
                    || StringUtils.containsIgnoreCase(className, "EOFException")
                    || StringUtils.containsIgnoreCase(message, "unexpected end of stream")
                    || StringUtils.containsIgnoreCase(message, "EOFException")
                    || StringUtils.containsIgnoreCase(message, "Connection reset")
                    || StringUtils.containsIgnoreCase(message, "Broken pipe")
                    || StringUtils.containsIgnoreCase(message, "SocketTimeoutException")
                    || StringUtils.containsIgnoreCase(message, "timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
    public Response<String> toolCall() {
        if (StringUtils.isBlank(workerUrl) || (!workerUrl.startsWith("http://") && !workerUrl.startsWith("https://"))) {
            log.error("Invalid worker URL: {}", workerUrl);
            return null;
        }
        dev.langchain4j.mcp.client.transport.McpTransport transport =
                dev.langchain4j.mcp.client.transport.http.HttpMcpTransport.builder()
                        .sseUrl(workerUrl + sseEndpoint)
                        .build();

        McpClient client = dev.langchain4j.mcp.client.DefaultMcpClient.builder()
                .transport(transport)
                .clientName("DebugToolCall")
                .build();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("browser_navigate")
                .arguments(JSON.toJSONString(Map.of("url", "https://bilibili.com")))
                .build();
        ToolExecutionResult result = client.executeTool(request);
        return Response.success(result.resultText());
    }

    @GetMapping("/debug/toollist")
    public Response<List<dev.langchain4j.agent.tool.ToolSpecification>> listTool() {
        if (StringUtils.isBlank(workerUrl) || (!workerUrl.startsWith("http://") && !workerUrl.startsWith("https://"))) {
            log.error("Invalid worker URL: {}", workerUrl);
            return null;
        }
        dev.langchain4j.mcp.client.transport.McpTransport transport =
                dev.langchain4j.mcp.client.transport.http.HttpMcpTransport.builder()
                        .sseUrl(workerUrl + sseEndpoint)
                        .build();

        McpClient client = dev.langchain4j.mcp.client.DefaultMcpClient.builder()
                .transport(transport)
                .clientName("DebugToolList")
                .build();
        return Response.success(client.listTools());
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
