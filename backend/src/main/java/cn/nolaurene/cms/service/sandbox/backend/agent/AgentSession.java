package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.McpHeartbeatService;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.message.TaskStatus;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.tool.CalculatorTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.McpToolProvider;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

/**
 * @author nolau
 * @date 2025/6/24
 * @description Agent Session 管理 AgentExecutor 的生命周期和前端连接状态
 */
@Slf4j
@Component
@Scope("prototype")
public class AgentSession {

    @Getter
    private Agent agent;

    @Getter
    @Setter
    private TaskStatus sessionStatus = TaskStatus.PENDING;

    @Value("${sandbox.backend.worker-mcp-url}")
    private String workerNativeMcpUrl;

    private AgentExecutor executor;

    private final AtomicBoolean frontendConnected = new AtomicBoolean(true);
    private volatile SseEmitter currentSseEmitter = null;
    @Getter
    private volatile CompletableFuture<Void> executionTask = null;

    public AgentSession() {
    }

    public void initialize(Agent agent, String workerUrl, String sseEndpoint,
                           AgentExecutorFactory agentExecutorFactory,
                           McpHeartbeatService mcpHeartbeatService) {
        this.agent = agent;
        this.agent.setPlanner(new Planner());
        this.agent.setExecutor(new Executor());

        // start langchain4j MCP clients
        McpClient browserMcpClient = startLangchain4jMcpClient(workerUrl, sseEndpoint, "BrowserMCP");
        agent.setBrowserMcpClient(browserMcpClient);

        // add to heartbeat service
        if (mcpHeartbeatService != null) {
            mcpHeartbeatService.addClient(browserMcpClient);
        }

        // verify browser tools available
        List<ToolSpecification> browserTools = browserMcpClient.listTools();
        if (browserTools == null || browserTools.isEmpty()) {
            log.error("No tools found in Browser MCP server at {}", workerUrl);
            throw new IllegalStateException("No tools found in Browser MCP server");
        }
        log.info("Browser MCP tools discovered: {}", browserTools.size());

        // start native mcp client (for file and shell tool)
        McpClient nativeMcpClient = startLangchain4jMcpClient(workerNativeMcpUrl, "/mcp/message/sse", "NativeMCP");
        agent.setNativeMcpClient(nativeMcpClient);

        List<ToolSpecification> nativeTools = nativeMcpClient.listTools();
        if (nativeTools == null || nativeTools.isEmpty()) {
            log.error("No tools found in Native MCP server at {}", workerNativeMcpUrl);
            throw new IllegalStateException("No tools found in Native MCP server");
        }
        log.info("Native MCP tools discovered: {}", nativeTools.size());

        // create McpToolProvider that aggregates both MCP clients
        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(browserMcpClient, nativeMcpClient)
                .build();
        agent.setToolProvider(toolProvider);

        // collect all tool specifications
        List<ToolSpecification> allTools = new ArrayList<>(browserTools);
        allTools.addAll(nativeTools);
        agent.setToolSpecifications(allTools);
        log.info("Total MCP tools available: {}", allTools.size());

        ToolRegistry registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        this.executor = agentExecutorFactory.createAgentExecutor(agent);
    }

    /**
     * Create langchain4j MCP client using SSE transport.
     */
    private McpClient startLangchain4jMcpClient(String workerUrl, String sseEndpoint, String clientName) {
        if (StringUtils.isBlank(workerUrl) || (!workerUrl.startsWith("http://") && !workerUrl.startsWith("https://"))) {
            log.error("Invalid worker URL: {}", workerUrl);
            throw new IllegalArgumentException("Invalid worker URL: " + workerUrl);
        }

        String sseUrl = workerUrl + sseEndpoint;
        log.info("Creating langchain4j MCP client [{}] connecting to: {}", clientName, sseUrl);

        McpTransport transport = HttpMcpTransport.builder()
                .sseUrl(sseUrl)
                .logRequests(true)
                .logResponses(true)
                .build();

        return DefaultMcpClient.builder()
                .transport(transport)
                .clientName(clientName)
                .build();
    }

    /**
     * 启动 Agent 执行流程 (前台模式)
     */
    public void reactFlow(String input, SseEmitter emitter) throws InterruptedException {
        if (this.sessionStatus == TaskStatus.RUNNING) {
            log.warn("AgentSession is already running.");
            try {
                emitter.send(SseEmitter.event().name("ERROR").data("Session already running."));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to send 'already running' message", e);
            }
            return;
        }

        this.sessionStatus = TaskStatus.RUNNING;
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);

        log.info("Starting AgentSession reactFlow for input: {}", input);

        setupSseEmitterListeners(emitter);

        try {
            executor.planAct(input, emitter);
            this.sessionStatus = TaskStatus.COMPLETED;
            log.info("AgentSession execution completed.");
            if (!frontendConnected.get() && currentSseEmitter != null) {
                try {
                    currentSseEmitter.send(SseEmitter.event().name("TASK_FINISHED_BG").data("Execution completed in background."));
                } catch (IOException e) {
                    log.debug("Could not send background finish signal.");
                }
            }

        } catch (Exception e) {
            log.error("Error during AgentSession execution", e);
            this.sessionStatus = TaskStatus.FAILED;
            if (frontendConnected.get() && currentSseEmitter != null) {
                try {
                    currentSseEmitter.send(SseEmitter.event().name("ERROR").data("Execution failed: " + e.getMessage()));
                    currentSseEmitter.complete();
                } catch (IOException ioException) {
                    log.error("Failed to send error to SSE", ioException);
                }
            } else if (!frontendConnected.get() && currentSseEmitter != null) {
                log.error("Execution failed in background: ", e);
            }
        } finally {
            this.currentSseEmitter = null;
            this.executionTask = null;
        }
    }

    /**
     * 恢复 Agent 执行流程的 SSE 流
     */
    public void resumeFlow(SseEmitter emitter) {
        log.info("Resuming AgentSession flow.");
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);

        setupSseEmitterListeners(emitter);

        try {
            emitter.send(SseEmitter.event().name("RESUMED").data("Connection resumed. Awaiting next events..."));
        } catch (IOException e) {
            log.error("Failed to send resume signal", e);
            frontendConnected.set(false);
            this.currentSseEmitter = null;
        }

        if (this.sessionStatus == TaskStatus.COMPLETED) {
            try {
                emitter.send(SseEmitter.event().name("TASK_ALREADY_FINISHED").data("Execution was already completed."));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to send completion status on resume", e);
            }
            this.currentSseEmitter = null;
        } else if (this.sessionStatus == TaskStatus.FAILED) {
            try {
                emitter.send(SseEmitter.event().name("TASK_ALREADY_FAILED").data("Execution had already failed."));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to send failure status on resume", e);
            }
            this.currentSseEmitter = null;
        }
    }

    private void setupSseEmitterListeners(SseEmitter sseEmitter) {
        sseEmitter.onCompletion(() -> {
            log.info("SSE connection completed for AgentSession (user likely left)");
            if (frontendConnected.compareAndSet(true, false)) {
                log.info("Frontend connection marked as disconnected via onCompletion.");
            }
            this.currentSseEmitter = null;
        });
        sseEmitter.onError((Throwable t) -> {
            log.warn("SSE connection encountered error for AgentSession", t);
            if (frontendConnected.compareAndSet(true, false)) {
                log.info("Frontend connection marked as disconnected via onError.");
            }
            this.currentSseEmitter = null;
        });
    }

    public boolean isFrontendConnected() {
        return frontendConnected.get();
    }

    public void sendMessage(String eventName, Object data) {
        if (this.currentSseEmitter != null && this.frontendConnected.get()) {
            try {
                this.currentSseEmitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data)
                        .id(String.valueOf(System.currentTimeMillis())));
                log.info("发送SSE消息: agentId={}, eventName={}", this.agent.getAgentId(), eventName);
            } catch (Exception e) {
                log.error("发送SSE消息失败: agentId={}, eventName={}", this.agent.getAgentId(), eventName, e);
            }
        } else {
            log.warn("无法发送SSE消息: agentId={}, eventName={}, emitter={}, connected={}",
                    this.agent.getAgentId(), eventName, this.currentSseEmitter != null, this.frontendConnected.get());
        }
    }

    public void setConversationPersistence(ConversationHistoryService service, String userId, String sessionId) {
        this.executor.setConversationPersistence(service, userId, sessionId);
    }
}