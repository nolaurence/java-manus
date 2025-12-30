package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMemory;
import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.llm.StreamResource;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.Function;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.ToolCall;
import cn.nolaurene.cms.common.sandbox.backend.model.data.*;
import cn.nolaurene.cms.common.sandbox.backend.model.message.AssistantMessageType;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.utils.ReActParser;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.TypeReference;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean; // 使用 AtomicBoolean
import java.util.stream.Collectors;


/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
@Slf4j
@Component
@Scope("prototype")
public class AgentExecutor {

    private int MAX_ROUNDS;
    private static final long ROUND_INTERVAL = 1000L; // 每轮间隔时间，单位毫秒
    private static final boolean USE_STREAM = false;
    private ToolRegistry tools;
    @Setter
    @Getter
    private String systemPrompt;
    private LlmClient llm;
    private Agent agent;
    private final ChatMemory memory = new ChatMemory();
    private static final String START_SIGNAL = "[START]";
    private static final String DONE_SIGNAL = "[DONE]";
    private static final int MCP_TOOL_RETRY_TIMES = 3;
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            5,
            20,
            0L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>()
    );

    // --- 新增/修改的状态管理 ---
    // 使用 AtomicBoolean 保证线程安全
    private final AtomicBoolean frontendConnected = new AtomicBoolean(true);
    // 持有当前的 SseEmitter 引用，用于后台模式下可能需要的错误通知或最终完成
    private volatile SseEmitter currentSseEmitter = null;

    // --- Persistence context ---
    private ConversationHistoryService conversationHistoryService;
    private String conversationUserId = "anonymous";
    private String conversationSessionId = null; // fallback to agentId if null

    public AgentExecutor() {
        this.MAX_ROUNDS = 30;
    }

    public void initialize(ToolRegistry tools, LlmClient llm, Agent agent) {
        this.tools = tools;
        this.llm = llm;
        this.MAX_ROUNDS = agent.getMaxLoop();
        this.agent = agent;
//        memory.add(new ChatMessage(ChatMessage.Role.system, systemPrompt));
    }

    public void setConversationPersistence(ConversationHistoryService conversationHistoryService, String userId, String sessionId) {
        this.conversationHistoryService = conversationHistoryService;
        if (userId != null && !userId.isEmpty()) {
            this.conversationUserId = userId;
        }
        this.conversationSessionId = (sessionId != null && !sessionId.isEmpty()) ? sessionId : this.agent.getAgentId();
    }

    public void planAct(String input, SseEmitter emitter) {
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);

        // 设置连接监听器
        setupSseEmitterListeners(emitter);
        saveUserMessage(input);
        memory.add(new ChatMessage(ChatMessage.Role.user, input));

        AgentStatus agentStatus = AgentStatus.IDLE;
        Plan plan = new Plan();
//        List<Step> globalSteps = new ArrayList<>();
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            try {
                switch (agentStatus) {
                    case IDLE:
                        log.info("[PLAN ACT] round {} start planning", round);
                        agentStatus = AgentStatus.PLANNING;
                        break;

                    case PLANNING:
                        // create plan
                        String rawPlan = agent.getPlanner().createPlan(llm, memory.getHistory());

                        log.info("[PLAN ACT] Raw plan for round {}: {}", round, rawPlan);
                        if (StringUtils.isBlank(rawPlan)) {
                            log.warn("[PLAN ACT] No plan created for round {}, skipping to next round.", round);
                            agentStatus = AgentStatus.IDLE;
                            continue; // 跳过当前轮次
                        }
                        String thought = ReActParser.parseThinking(rawPlan);
                        syncRespondThought(START_SIGNAL, emitter);
                        syncRespondThought(thought, emitter);
                        syncRespondThought(DONE_SIGNAL, emitter);
                        syncRespondContent(rawPlan, emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        // persist assistant plan content
                        saveAssistantMessage("**Thinking:**\n" + thought + "\n\n**Response:**\n" + rawPlan, SSEEventType.MESSAGE);
                        plan = ReActParser.parsePlan(rawPlan);
                        if (null == plan) {
                            log.error("[PLAN ACT] Failed to parse plan for round {}, skipping to next round.", round);
                            continue; // 跳过当前轮次
                        }

                        // 规划出的step全部置为pending
                        plan.getSteps().forEach(step -> step.setStatus(StepEventStatus.pending.getCode()));
                        syncRespondPlan(plan, emitter);
                        saveAssistantMessage(JSON.toJSONString(plan), SSEEventType.PLAN);

                        agentStatus = AgentStatus.EXECUTING;
                        break;

                    case EXECUTING:
                        Optional<Step> currentStepOpt = plan.getSteps().stream()
                                .filter(step -> StepEventStatus.pending.getCode().equals(step.getStatus()))
                                .findFirst();
                        if (currentStepOpt.isEmpty()) {
                            log.warn("[PLAN ACT] No pending steps found for round {}, skipping to next round.", round);
                            agentStatus = AgentStatus.IDLE;
                            break; // 跳过当前轮次
                        }
                        Step currentStep = currentStepOpt.get();
                        currentStep.setStatus(StepEventStatus.running.getCode());

                        reportStep(StepEventStatus.running, currentStep.getDescription(), emitter);
                        syncRespondPlan(plan, emitter);
                        conversationHistoryService.updateLastPlan(agent.getAgentId(), plan);

                        String executionCommand = agent.getExecutor().executeStep(llm, memory.getHistory(), plan.getGoal(), currentStep.getDescription(), agent.getXmlToolsInfo());
                        List<ToolCall> toolCallsFromAI = ReActParser.parseToolCallsFromContent(executionCommand);

                        log.info("[PLAN ACT] Execute command for round {}: {}", round, executionCommand);
                        String observation = executeTools(round, toolCallsFromAI, emitter);
                        currentStep.setResult(observation);
                        currentStep.setStatus(StepEventStatus.completed.getCode());
                        reportStep(StepEventStatus.completed, currentStep.getDescription(), emitter);  // 这里应该没问题
                        syncRespondPlan(plan, emitter);
                        conversationHistoryService.updateLastPlan(agent.getAgentId(), plan);

                        agentStatus = AgentStatus.UPDATING;
                        break;

                    case UPDATING:
                        List<Step> pendingSteps = plan.getSteps().stream().filter(step -> step.getStatus().equals(StepEventStatus.pending.getCode())).collect(Collectors.toList());
                        List<Step> finishedSteps = plan.getSteps().stream().filter(step -> !step.getStatus().equals(StepEventStatus.pending.getCode())).collect(Collectors.toList());

                        String updatedStepsString = agent.getPlanner().updatePlan(llm, plan);
                        log.info("[PLAN ACT] Updated steps for round {}: {}", round, updatedStepsString);

                        List<String> newSteps = ReActParser.parseStepDescriptions(updatedStepsString);
                        if (CollectionUtils.isEmpty(newSteps)) {
                            log.warn("[PLAN ACT] No new steps found in updated steps for round {}, skipping to conclude round.", round);
                            agentStatus = AgentStatus.CONCLUDING;
                            break; // 跳过当前轮次
                        }

                        finishedSteps.addAll(newSteps.stream().map(stepString -> {
                            Step step = new Step();
                            step.setDescription(stepString);
                            step.setStatus(StepEventStatus.pending.getCode());
                            return step;
                        }).collect(Collectors.toList()));
                        // 更新新的 globalSteps
                        plan.setSteps(new ArrayList<>(finishedSteps));
                        log.info("[PLAN ACT] Updated global steps for round {}: {}", round, JSON.toJSONString(plan.getSteps()));

                        syncRespondPlan(plan, emitter);
                        conversationHistoryService.updateLastPlan(agent.getAgentId(), plan);

                        // update 后继续执行
                        agentStatus = AgentStatus.EXECUTING;
                        break;

                    case CONCLUDING:
                        String conclusion = agent.getExecutor().conclude(llm, memory.getHistory());

                        // response
                        syncRespondThought(START_SIGNAL, emitter);
                        syncRespondThought(DONE_SIGNAL, emitter);
                        syncRespondContent(conclusion, emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        // persist assistant final content for conclusion
                        saveAssistantMessage(conclusion, SSEEventType.MESSAGE);
                        agentStatus = AgentStatus.IDLE;
                        round = MAX_ROUNDS + 1;  // 跳出for循环
                        break;
                }
            } catch (Exception e) {
                log.error("[PLAN ACT] Error when creating plan for round: {}, error: ", round, e);
                break;
            }
        }
    }

    // --- 工具执行逻辑 (区分前台/后台) ---
    private String executeTools(int round, List<ToolCall> toolCallsFromAI, SseEmitter sseEmitterOpt) {
        List<String> mcpToolNames = agent.getMcpTools().stream().map(McpSchema.Tool::getName).collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(toolCallsFromAI)) {
            long toolStartTime = System.currentTimeMillis();
            try {
                List<String> observations = new ArrayList<>();
                for (ToolCall toolCall : toolCallsFromAI) {
                    // --- 检查点：工具执行前检查连接状态 ---
                    if (!frontendConnected.get() && sseEmitterOpt != null) {
                        log.info("Frontend disconnected before executing tool {}, switching to background for tool execution.", toolCall.getFunction().getName());
                        sseEmitterOpt = null; // 后续操作转为后台
                    }

                    String toolName = toolCall.getFunction().getName();
                    Map<String, Object> toolInput;
                    try {
                        toolInput = JSON.parseObject(toolCall.getFunction().getArguments().trim(), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        log.error("Failed to parse tool arguments for tool {}: {}", toolName, toolCall.getFunction().getArguments(), e);
                        String errorMsg = "Failed to parse arguments for tool: " + toolName;
                        reportStep(StepEventStatus.failed, errorMsg, sseEmitterOpt);
                        continue;
                    }

                    log.info("round: {}, tool call: {}, input: {}", round, toolName, JSON.toJSONString(toolInput));

                    String observation = "Observation not set";
                    AssistantMessageType messageType; // Default

                    if (!mcpToolNames.contains(toolName)) {
                        String errorMsg = "Unknown tool: " + toolName;
                        log.warn(errorMsg);
                        observation = errorMsg;
                        observations.add(observation);
                        continue;
                    }

                    McpSyncClient mcpClient = null;
                    if (toolName.startsWith("browser")) {
                        messageType = AssistantMessageType.BROWSER;
                        mcpClient = agent.getBrowserMcpClient();
                    } else if (toolName.startsWith("shell")) {
                        messageType = AssistantMessageType.SHELL;
                        mcpClient = agent.getNativeMcpClient();
                    } else if (toolName.startsWith("file")) {
                        messageType = AssistantMessageType.FILE;
                        mcpClient = agent.getNativeMcpClient();
                    } else {
                        String errorMsg = "Unknown tool: " + toolName;
                        log.warn(errorMsg);
                        observation = errorMsg;

                        observations.add(observation);
                        continue;
                    }
                    // 模拟延迟 (可选)
                    try { Thread.sleep(200L); } catch (InterruptedException ignored) {}

                    ToolEventData toolEventData = new ToolEventData();
                    toolEventData.setTimestamp(System.currentTimeMillis());
                    toolEventData.setName(messageType.getMessage());
                    toolEventData.setFunction(toolCall.getFunction().getName());
                    try {
                        toolEventData.setArgs(JSON.parseObject(toolCall.getFunction().getArguments(), new TypeReference<>() {}));
                    } catch (Exception ignored) {}
                    reportTool(toolEventData, sseEmitterOpt); // 报告工具调用事件

                    McpSchema.CallToolResult callToolResult = null;
                    Exception lastException = null;
                    for (int i = 0; i < MCP_TOOL_RETRY_TIMES; i++) {
                        try {
                            callToolResult = mcpClient.callTool(new McpSchema.CallToolRequest(toolName, toolInput));
                            lastException = null;
                            break;
                        } catch (Exception e) {
                            log.warn("调用mcp工具【{}】失败，第 {} 次重试", toolName, i + 1, e);
                            lastException = e;
                            if (i < MCP_TOOL_RETRY_TIMES - 1) {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException("重试被中断", ie);
                                }
                            }
                        }
                    }

                    if (lastException != null) {
                        observation = "Tool call error after retries: " + lastException.getMessage();
                    } else if (null == callToolResult || (null != callToolResult.getIsError() && callToolResult.getIsError())) {
                        observation = "Tool call error: " + JSON.toJSONString(callToolResult != null ? callToolResult.getContent() : "Unknown error");
                    } else {
                        observation = JSON.toJSONString(callToolResult.getContent());
                    }

                    log.info("add observation to memory: {}", observation);
                    observations.add(observation);
                }
                return String.join("\n", observations);
            } catch (Exception e) {
                log.error("execute tool calls error for round {}", round, e);
                handleError("Unexpected error during tool execution: " + e.getMessage(), round);
            }
            log.info("round: {}, tool calls executed in {} ms", round, System.currentTimeMillis() - toolStartTime);
        }
        return "";
    }

    // --- 修改 async* 方法或创建新方法来处理前台/后台 ---
    // --- 修改后的事件处理方法 ---
    private void asyncResponse(MessageEventData messageEvent, SseEmitter sseEmitter) {
        // 由于可能在流读取循环中调用，且状态可能已变，需要检查
        if (frontendConnected.get() && sseEmitter != null) {
            // 前台模式：异步推送
            executor.submit(() -> {
                try {
                    // 再次检查，因为状态可能在提交任务后改变
                    if (frontendConnected.get()) {
                        sseEmitter.send(SseEmitter.event()
                                .name(SSEEventType.MESSAGE.getType())
                                .data(JSON.toJSONString(messageEvent))
                                .id(String.valueOf(System.currentTimeMillis())));
                    }
                } catch (Exception e) {
                    log.error("Error sending message event via SSE", e);
                    if (frontendConnected.compareAndSet(true, false)) {
                        log.info("Send error, marking frontend as disconnected.");
                    }
                }
            });
        } else {
            // 后台模式：仅记录日志
            logSseEvent(SSEEventType.MESSAGE.getType(), messageEvent);
        }
    }

    private void syncRespondThought(String reasoningContent, SseEmitter sseEmitter) {
        // 由于可能在流读取循环中调用，且状态可能已变，需要检查
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setReasoningContentDelta(reasoningContent);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && sseEmitter != null) {
            // 前台模式：同步推送
            try {
                // 再次检查，因为状态可能在提交任务后改变
                if (frontendConnected.get()) {
                    sseEmitter.send(SseEmitter.event()
                            .name(SSEEventType.MESSAGE.getType())
                            .data(JSON.toJSONString(messageEvent))
                            .id(String.valueOf(System.currentTimeMillis())));
                }
            } catch (Exception e) {
                log.error("Error sending message event via SSE", e);
                if (frontendConnected.compareAndSet(true, false)) {
                    log.info("Send error, marking frontend as disconnected.");
                }
            }
        } else {
            // 后台模式：仅记录日志
            logSseEvent(SSEEventType.MESSAGE.getType(), messageEvent);
        }
    }

    private void syncRespondNonStreamContent(String content, SseEmitter emitter) {
        syncRespondThought(START_SIGNAL, emitter);
        syncRespondThought(DONE_SIGNAL, emitter);
        syncRespondContent(content, emitter);
        syncRespondContent(DONE_SIGNAL, emitter);
    }

    private void syncRespondContent(String content, SseEmitter sseEmitter) {
        // 由于可能在流读取循环中调用，且状态可能已变，需要检查
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setContentDelta(content);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && sseEmitter != null) {
            // 前台模式：异步推送
            try {
                // 再次检查，因为状态可能在提交任务后改变
                if (frontendConnected.get()) {
                    sseEmitter.send(SseEmitter.event()
                            .name(SSEEventType.MESSAGE.getType())
                            .data(JSON.toJSONString(messageEvent))
                            .id(String.valueOf(System.currentTimeMillis())));
                }
            } catch (Exception e) {
                log.error("Error sending message event via SSE", e);
                if (frontendConnected.compareAndSet(true, false)) {
                    log.info("Send error, marking frontend as disconnected.");
                }
            }
        } else {
            // 后台模式：仅记录日志
            logSseEvent(SSEEventType.MESSAGE.getType(), messageEvent);
        }
    }

    private void syncRespondPlan(Plan plan, SseEmitter sseEmitter) {
        PlanEventData eventData = new PlanEventData();
        eventData.setId(String.valueOf(System.currentTimeMillis()));
        eventData.setTitle(plan.getTitle());
        eventData.setGoal(plan.getGoal());
        eventData.setStatus("created");
        eventData.setSteps(plan.getSteps().stream().map(step -> {
            StepEventData stepData = new StepEventData();
            stepData.setDescription(step.getDescription());
            stepData.setStatus(step.getStatus());
            stepData.setTimestamp(System.currentTimeMillis());
            return stepData;
        }).collect(Collectors.toList()));

        if (frontendConnected.get() && sseEmitter != null) {
            try {
                sseEmitter.send(SseEmitter.event()
                        .name(SSEEventType.PLAN.getType())
                        .data(eventData)
                        .id(String.valueOf(System.currentTimeMillis())));
            } catch (Exception e) {
                log.error("Error sending message event via SSE", e);
                if (frontendConnected.compareAndSet(true, false)) {
                    log.info("Send error, marking frontend as disconnected.");
                }
            }
        } else {
            // 后台模式：仅记录日志
            logSseEvent(SSEEventType.MESSAGE.getType(), eventData);
        }
    }

    private void asyncStep(StepEventStatus status, String description, SseEmitter sseEmitter) {
        StepEventData data = new StepEventData();
        data.setTimestamp(System.currentTimeMillis());
        data.setStatus(status.getCode());
        data.setDescription(description);

        if (frontendConnected.get() && sseEmitter != null) {
            // 前台模式：异步推送
            executor.submit(() -> {
                try {
                    if (frontendConnected.get()) {
                        sseEmitter.send(SseEmitter.event()
                                .name(SSEEventType.STEP.getType())
                                .data(data)
                                .id(String.valueOf(System.currentTimeMillis())));
                    }
                } catch (Exception e) {
                    log.error("Error sending step event via SSE", e);
                    if (frontendConnected.compareAndSet(true, false)) {
                        log.info("Send error, marking frontend as disconnected.");
                    }
                }
            });
        } else {
            // 后台模式：仅记录日志
            logSseEvent(SSEEventType.STEP.getType(), data);
        }
    }

    private void asyncToolMessage(ToolCall toolCall, SseEmitter sseEmitter, AssistantMessageType messageType) {
        ToolEventData data = new ToolEventData();
        data.setTimestamp(System.currentTimeMillis());
        data.setName(messageType.getMessage());
        data.setFunction(toolCall.getFunction().getName());
        try {
            data.setArgs(JSON.parseObject(toolCall.getFunction().getArguments(), new TypeReference<>() {}));
        } catch (Exception e) {
            log.warn("Failed to parse tool args for tool event", e);
        }

        if (frontendConnected.get() && sseEmitter != null) {
            // 前台模式：异步推送
            executor.submit(() -> {
                try {
                    if (frontendConnected.get()) {
                        sseEmitter.send(SseEmitter.event()
                                .name(SSEEventType.TOOL.getType())
                                .data(data)
                                .id(String.valueOf(System.currentTimeMillis())));
                    }
                } catch (Exception e) {
                    log.error("Error sending tool event via SSE", e);
                    if (frontendConnected.compareAndSet(true, false)) {
                        log.info("Send error, marking frontend as disconnected.");
                    }
                }
            });
        } else {
            // 后台模式：仅记录日志
            logSseEvent(SSEEventType.TOOL.getType(), data);
        }
    }

    // --- 新增：统一的步骤报告方法 ---
    private void reportStep(StepEventStatus status, String description, SseEmitter sseEmitterOpt) {
        if (frontendConnected.get() && sseEmitterOpt != null) {
            asyncStep(status, description, sseEmitterOpt);
        } else {
            StepEventData data = new StepEventData();
            data.setTimestamp(System.currentTimeMillis());
            data.setStatus(status.getCode());
            data.setDescription(description);
            logSseEvent(SSEEventType.STEP.getType(), data); // 后台记录
        }

        // save step info to database
        switch(status) {
            case running:
                conversationHistoryService.addStep(agent.getUserId(), agent.getAgentId(), description);
                break;
            case completed:
                conversationHistoryService.updateLastStepStatus(agent.getAgentId(), StepEventStatus.completed.getCode());
                break;
            case failed:
                conversationHistoryService.updateLastStepStatus(agent.getAgentId(), StepEventStatus.failed.getCode());
                break;
        }
    }

    // --- 新增：统一的工具报告方法 ---
    private void reportTool(ToolEventData toolEventData, SseEmitter sseEmitterOpt) {
        if (frontendConnected.get() && sseEmitterOpt != null) {
            // 前台模式：异步推送
            executor.submit(() -> {
                try {
                    if (frontendConnected.get()) {
                        sseEmitterOpt.send(SseEmitter.event()
                                .name(SSEEventType.TOOL.getType())
                                .data(toolEventData)
                                .id(String.valueOf(System.currentTimeMillis())));
                    }
                } catch (Exception e) {
                    log.error("Error sending tool event via SSE", e);
                    if (frontendConnected.compareAndSet(true, false)) {
                        log.info("Send error, marking frontend as disconnected.");
                    }
                }
            });
        } else {
            // 后台模式：仅记录日志
            logSseEvent(SSEEventType.TOOL.getType(), toolEventData);
        }
        saveAssistantMessage(JSON.toJSONString(toolEventData), SSEEventType.TOOL);
    }

    // --- 新增：后台模式下的日志记录 ---
    private void logSseEvent(String eventType, Object eventData) {
        // 可以根据需要记录到文件、数据库或简单的内存缓冲区
        // 这里仅打印 DEBUG 日志
        if (log.isDebugEnabled()) {
            log.debug("BG Event - Type: {}, Data: {}", eventType, JSON.toJSONString(eventData));
        }
    }

    // --- 新增：后台模式下的错误处理 ---
    private void handleError(String errorMsg, int round) {
        log.error("Background execution error in round {}: {}", round, errorMsg);
        // 记录错误事件
        StepEventData errorEvent = new StepEventData();
        errorEvent.setTimestamp(System.currentTimeMillis());
        errorEvent.setStatus(StepEventStatus.failed.getCode());
        errorEvent.setDescription(errorMsg);
        logSseEvent(SSEEventType.STEP.getType(), errorEvent);

        // 如果前端恰好在此刻重新连接（虽然概率低），可以尝试通知
        // 但通常后台错误不需要强制推送到前端，除非有特定需求
        // 可以通过外部 Session 管理器的状态查询来获知
    }


    // --- 设置 SSE 监听器 ---
    private void setupSseEmitterListeners(SseEmitter sseEmitter) {
        sseEmitter.onCompletion(() -> {
            log.info("SSE connection completed/onCompletion (user likely left)");
            if (frontendConnected.compareAndSet(true, false)) { // 原子性设置
                log.info("Frontend connection marked as disconnected via onCompletion.");
            }
            // 不要在这里 complete() 或 stop anything, just update state
        });
        sseEmitter.onError((Throwable t) -> {
            log.warn("SSE connection encountered error/onError", t);
            if (frontendConnected.compareAndSet(true, false)) { // 原子性设置
                log.info("Frontend connection marked as disconnected via onError.");
            }
            // 不要在这里 stop anything, just update state
        });
    }


    // ... rest of the class remains largely the same ...
    private McpSchema.CallToolResult callMcpToolWithRetry(String toolName, Map<String, Object> arguments, int retryCount) {
        // ... implementation remains the same ...
        int attempts = 0;
        while (attempts < retryCount) {
            try {
                return agent.getBrowserMcpClient().callTool(new McpSchema.CallToolRequest(toolName, arguments));
            } catch (Exception e) {
                log.warn("调用mcp工具【{}】失败，第 {} 次重试", toolName, attempts + 1, e);
                attempts++;
                if (attempts >= retryCount) {
                    throw e;
                }
                try {
                    Thread.sleep(500); // 可根据需要调整重试间隔
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        return null;
    }

    private void saveUserMessage(String content) {
        if (conversationHistoryService == null) {
            return;
        }
        try {
            ConversationRequest req = new ConversationRequest();
            req.setUserId(conversationUserId);
            req.setSessionId(conversationSessionId != null ? conversationSessionId : agent.getAgentId());
            req.setEventType(SSEEventType.MESSAGE);
            req.setMessageType(ConversationHistoryDO.MessageType.USER);
            req.setContent(content);
            req.setMetadata(null);
            conversationHistoryService.saveConversation(req);
        } catch (Exception e) {
            log.warn("failed to persist assistant message", e);
        }
    }

    private void saveAssistantMessage(String content, SSEEventType eventType) {
        if (conversationHistoryService == null) {
            return;
        }
        try {
            ConversationRequest req = new ConversationRequest();
            req.setUserId(conversationUserId);
            req.setSessionId(conversationSessionId != null ? conversationSessionId : agent.getAgentId());
            req.setMessageType(ConversationHistoryDO.MessageType.ASSISTANT);
            req.setEventType(eventType);
            req.setContent(content);
            req.setMetadata(null);
            conversationHistoryService.saveConversation(req);
        } catch (Exception e) {
            log.warn("failed to persist assistant message", e);
        }
    }

    public static void main(String[] args) {
        String rawJSON = "{\"id\":\"01984c5f468a537577fa3bf2bef96606\",\"object\":\"chat.completion.chunk\",\"created\":1753627969,\"model\":\"Qwen/Qwen3-8B\",\"choices\":[{\"index\":0,\"delta\":{\"content\":null,\"reasoning_content\":null,\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"01984c5f5f6a7e0c178bdc9407f41ea8\",\"type\":\"function\",\"function\":{\"name\":\"browser_navigate\",\"arguments\":\"\"}}]},\"finish_reason\":null}],\"system_fingerprint\":\"\",\"usage\":{\"prompt_tokens\":2727,\"completion_tokens\":315,\"total_tokens\":3042,\"completion_tokens_details\":{\"reasoning_tokens\":313}}}";
        Object eval = JSONPath.eval(rawJSON, "$.choices[0].delta.tool_calls");
        System.out.println(eval.getClass());
        List<ToolCall> toolCalls = JSON.parseArray(JSON.toJSONString(JSONPath.eval(rawJSON, "$.choices[0].delta.tool_calls")), ToolCall.class);
    }
}