package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.llm.SiliconFlowClient;
import cn.nolaurene.cms.service.sandbox.backend.message.TaskStatus;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.tool.CalculatorTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture; // 用于异步执行

/**
 * @author nolau
 * @date 2025/6/24
 * @description Agent Session 管理 AgentExecutor 的生命周期和前端连接状态
 */
@Slf4j
public class AgentSession {

    @Getter
    private final Agent agent;

    @Getter
    @Setter
    private TaskStatus sessionStatus = TaskStatus.PENDING;

    private final AgentExecutor executor;

    // --- 新增：连接状态管理 ---
    // 使用 AtomicBoolean 确保多线程下的可见性
    private final AtomicBoolean frontendConnected = new AtomicBoolean(true);
    // 持有当前的 SseEmitter 引用
    private volatile SseEmitter currentSseEmitter = null;
    // 用于跟踪执行任务
    @Getter
    private volatile CompletableFuture<Void> executionTask = null;

    // private final TemplateEngine templateEngine = new TemplateEngine(); // 注释掉未使用的

    public AgentSession(Agent agent, String workerUrl, String sseEndpoint) {
        this.agent = agent;
        this.agent.setPlanner(new Planner());
        this.agent.setExecutor(new Executor());

        // start mcp client
        LlmClient llmClient = new SiliconFlowClient(agent.getLlmEndpoint(), agent.getLlmApiKey());
        McpSyncClient mcpClient = startMcpClient(workerUrl, sseEndpoint);
        agent.setMcpClient(mcpClient);

        // ask mcp server to get all tool schemas
        McpSchema.ListToolsResult listToolsResult = mcpClient.listTools();
        if (listToolsResult == null || listToolsResult.getTools() == null || listToolsResult.getTools().isEmpty()) {
            log.error("No tools found in MCP server at {}", workerUrl);
            throw new IllegalStateException("No tools found in MCP server");
        }
        agent.setMcpTools(listToolsResult.getTools());
        agent.setXmlToolsInfo(agent.getMcpTools().stream().map(this::renderXmlTools).collect(Collectors.joining("\n\n")));
        agent.setTools(listToolsResult.getTools().stream().map(this::convertToOpenaiFunction).collect(Collectors.toList()));

//        String systemPrompt = renderSystemPrompt(agent);
        ToolRegistry registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        this.executor = new AgentExecutor(registry, llmClient, agent);
    }

    /**
     * 启动 Agent 执行流程 (前台模式)
     * @param input 用户输入
     * @param emitter SSE Emitter
     * @throws InterruptedException 如果线程被中断
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
        this.frontendConnected.set(true); // 初始标记为连接

        log.info("Starting AgentSession reactFlow for input: {}", input);

        // 设置 SSE 连接监听器
        setupSseEmitterListeners(emitter);

        // 注意：AgentExecutor 内部也需要处理连接断开并切换到后台模式
        try {
            // 假设 AgentExecutor 内部已经实现了根据 frontendConnected 状态切换逻辑
            executor.planAct(input, emitter); // 调用修改后的 streamReact
            // 如果执行到这里，说明正常完成（无论前台还是后台）
            this.sessionStatus = TaskStatus.COMPLETED;
            log.info("AgentSession execution completed.");
            // 如果最后是在后台完成的，尝试通知（如果 emitter 还有效）
            if (!frontendConnected.get() && currentSseEmitter != null) {
                try {
                    currentSseEmitter.send(SseEmitter.event().name("TASK_FINISHED_BG").data("Execution completed in background."));
                    // 不 complete() 已断开的 emitter
                } catch (IOException e) {
                    log.debug("Could not send background finish signal.");
                }
            }

        } catch (Exception e) {
            log.error("Error during AgentSession execution", e);
            this.sessionStatus = TaskStatus.FAILED;
            // 尝试通知前端错误（如果还连接着）
            if (frontendConnected.get() && currentSseEmitter != null) {
                try {
                    currentSseEmitter.send(SseEmitter.event().name("ERROR").data("Execution failed: " + e.getMessage()));
                    currentSseEmitter.complete();
                } catch (IOException ioException) {
                    log.error("Failed to send error to SSE", ioException);
                }
            } else if (!frontendConnected.get() && currentSseEmitter != null) {
                // 后台模式下记录错误
                log.error("Execution failed in background: ", e);
                // 可以通过其他方式记录后台错误，例如日志或外部存储
            }
        } finally {
            // 清理引用
            this.currentSseEmitter = null;
            this.executionTask = null;
        }
    }

    /**
     * 恢复 Agent 执行流程的 SSE 流 (供前端重新连接时调用)
     * @param emitter 新的 SSE Emitter
     */
    public void resumeFlow(SseEmitter emitter) {
        log.info("Resuming AgentSession flow.");
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true); // 标记为重新连接

        // 重新设置监听器
        setupSseEmitterListeners(emitter);

        // 通知 AgentExecutor 恢复（如果它支持的话）
        // 假设 AgentExecutor 有 resume 方法
        // executor.resume(emitter); // 如果 AgentExecutor 实现了 resume 逻辑

        // 或者，AgentExecutor 内部通过检查 frontendConnected 状态来决定是否推送新事件
        // 这里主要是更新 Session 的状态和 emitter

        // 可以发送一个恢复信号给前端
        try {
            emitter.send(SseEmitter.event().name("RESUMED").data("Connection resumed. Awaiting next events..."));
        } catch (IOException e) {
            log.error("Failed to send resume signal", e);
            // 如果发送失败，立即标记为断开
            frontendConnected.set(false);
            this.currentSseEmitter = null;
        }

        // 注意：如果任务已经完成或失败，应该告知前端
        if (this.sessionStatus == TaskStatus.COMPLETED) {
            try {
                emitter.send(SseEmitter.event().name("TASK_ALREADY_FINISHED").data("Execution was already completed."));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to send completion status on resume", e);
            }
            this.currentSseEmitter = null; // 清除
        } else if (this.sessionStatus == TaskStatus.FAILED) {
            try {
                emitter.send(SseEmitter.event().name("TASK_ALREADY_FAILED").data("Execution had already failed."));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to send failure status on resume", e);
            }
            this.currentSseEmitter = null; // 清除
        }
        // 如果是 RUNNING，则等待 AgentExecutor 下一次事件推送或任务完成
    }


    /**
     * start mcp client
     */
    public McpSyncClient startMcpClient(String workerUrl, String sseEndpoint) {
        if (StringUtils.isBlank(workerUrl) || (!workerUrl.startsWith("http://") && !workerUrl.startsWith("https://"))) {
            log.error("Invalid worker URL: {}", workerUrl);
            return null;
        }

        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(workerUrl)
                .sseEndpoint(sseEndpoint)
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(20))
                .capabilities(McpSchema.ClientCapabilities.builder()
//                        .roots(true)
                        .sampling()
                        .build())
//                .sampling(request -> new McpSchema.CreateMessageRequest(response))  // sampling allow server to use llm
                .build();
        client.initialize();

        return client;
    }

    private JSONObject convertToOpenaiFunction(McpSchema.Tool mcpTool) {
        JSONObject function = new JSONObject();
        function.put("name", mcpTool.getName());
        function.put("description", mcpTool.getDescription());
        function.put("parameters", mcpTool.getInputSchema());

        JSONObject functionOuter = new JSONObject();
        functionOuter.put("type", "function");
        functionOuter.put("function", function);
        return functionOuter;
    }

    // --- 新增：设置 SSE 监听器 ---
    private void setupSseEmitterListeners(SseEmitter sseEmitter) {
        sseEmitter.onCompletion(() -> {
            log.info("SSE connection completed for AgentSession (user likely left)");
            if (frontendConnected.compareAndSet(true, false)) { // 原子性设置
                log.info("Frontend connection marked as disconnected via onCompletion.");
            }
            this.currentSseEmitter = null; // 清除引用
            // 不要在这里 stop anything, just update state
            // AgentExecutor 应该通过检查 frontendConnected 来感知变化
        });
        sseEmitter.onError((Throwable t) -> {
            log.warn("SSE connection encountered error for AgentSession", t);
            if (frontendConnected.compareAndSet(true, false)) { // 原子性设置
                log.info("Frontend connection marked as disconnected via onError.");
            }
            this.currentSseEmitter = null;
            // 不要在这里 stop anything, just update state
        });
    }

    private String renderXmlTools(McpSchema.Tool mcpTool) {
        List<String> xmlLines = new ArrayList<>();
        xmlLines.add("<tool>");
        xmlLines.add("<name>" + mcpTool.getName() + "</name>");
        xmlLines.add("<description>" + mcpTool.getDescription() + "</description>");
        if (mcpTool.getInputSchema() != null) {
            xmlLines.add("<arguments>\n" + JSON.toJSONString(mcpTool.getInputSchema()) + "\n</arguments>");
        }
        xmlLines.add("</tool>");
        return String.join("\n", xmlLines);
    }

    // --- 新增：Getter for connection status (可选) ---
    public boolean isFrontendConnected() {
        return frontendConnected.get();
    }

    // configure conversation persistence context for downstream executor
    public void setConversationPersistence(ConversationHistoryService service, String userId, String sessionId) {
        this.executor.setConversationPersistence(service, userId, sessionId);
    }
}