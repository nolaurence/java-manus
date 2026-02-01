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
import cn.nolaurene.cms.service.sandbox.backend.SseMessageForwardService;
import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.entity.AgentSessionServerDO;
import cn.nolaurene.cms.service.AgentSessionServerService;
import cn.nolaurene.cms.utils.Fastjson2LenientToolParser;
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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean; // 使用 AtomicBoolean
import java.util.stream.Collectors;
import javax.annotation.Resource;


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

    private AgentContext context;

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

    private String localServerIp = "127.0.0.1";

    // --- Persistence context ---
    private ConversationHistoryService conversationHistoryService;
    private String conversationUserId = "anonymous";
    private String conversationSessionId = null; // fallback to agentId if null

    @Resource
    private SseMessageForwardService sseMessageForwardService;

    @Resource
    private AgentSessionServerService agentSessionServerService;

    public AgentExecutor() {
        this.MAX_ROUNDS = 30;
        try {
            this.localServerIp = getLocalIpAddress();
        } catch (Exception e) {
            log.warn("获取本地IP失败，使用默认值127.0.0.1", e);
        }
    }

    private boolean shouldDirectSend(String agentId) {
        if (agent == null || agent.getAgentId() == null) {
            return true;
        }

        AgentSessionServerDO serverInfo = agentSessionServerService.getByAgentId(agentId);
        if (serverInfo == null || serverInfo.getServerIp() == null) {
            log.warn("无法获取agent {} 的服务器信息，默认直接发送", agentId);
            return true;
        }

        return localServerIp.equals(serverInfo.getServerIp());
    }

    private void sendOrForwardMessage(SseEmitter emitter, String eventName, Object data) {
        if (!shouldDirectSend(agent.getAgentId())) {
            AgentSessionServerDO serverInfo = agentSessionServerService.getByAgentId(agent.getAgentId());
            if (serverInfo != null) {
                log.info("转发消息到服务器 {}: agentId={}, eventName={}", serverInfo.getServerIp(), agent.getAgentId(), eventName);
                sseMessageForwardService.forwardMessage(serverInfo.getServerIp(), serverInfo.getServerPort(), agent.getAgentId(), eventName, data);
                return;
            }
        }

        try {
            if (emitter != null) {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data)
                        .id(String.valueOf(System.currentTimeMillis())));
            }
        } catch (Exception e) {
            log.error("直接发送SSE消息失败: agentId={}, eventName={}", agent.getAgentId(), eventName, e);
            if (frontendConnected.compareAndSet(true, false)) {
                log.info("发送错误，标记前端为断开连接");
            }
        }
    }

    public void initialize(ToolRegistry tools, LlmClient llm, Agent agent) {
        this.tools = tools;
        this.llm = llm;
        this.MAX_ROUNDS = agent.getMaxLoop();
        this.agent = agent;
        this.context = new AgentContext();
        this.context.setToolList(new ArrayList<>());
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

                        List<Step> completedSteps = plan.getSteps()
                                .stream()
                                .filter(step -> step.getStatus().equals(StepEventStatus.completed.getCode()))
                                .collect(Collectors.toList());

                        // remove all completed steps' result except last one
                        if (CollectionUtils.isNotEmpty(completedSteps)) {
                            for (int idx = completedSteps.size() - 2; idx >= 0; idx--) {
                                completedSteps.get(idx).setResult("");
                            }
                        }

                        String executionCommand = agent.getExecutor().executeStep(llm, completedSteps, plan.getGoal(), currentStep.getDescription(), agent.getXmlToolsInfo(), this.context);
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
        // 实现一下从agent.getMcpTools 中获取工具名称和inputschema的所有key值，并组装成map
        Map<String, McpSchema.JsonSchema> toolInputSchemaMap = agent.getMcpTools().stream().collect(
                Collectors.toMap(McpSchema.Tool::getName, McpSchema.Tool::getInputSchema));

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
                    String observation;
                    AssistantMessageType messageType; // Default
                    if (!toolInputSchemaMap.containsKey(toolName)) {
                        String errorMsg = "[Tool Execution] Unknown tool: " + toolName;
                        log.warn(errorMsg);
                        observation = errorMsg;
                        observations.add(observation);
                        continue;
                    }

                    Map<String, Object> toolInput;
                    try {
                        String arguments = toolCall.getFunction().getArguments();
                        if (arguments == null || arguments.trim().isEmpty()) {
                            log.error("Tool {} has empty or null arguments", toolName);
                            String errorMsg = "Tool " + toolName + " has empty or null arguments";
                            reportStep(StepEventStatus.failed, errorMsg, sseEmitterOpt);
                            continue;
                        }
                        toolInput = Fastjson2LenientToolParser.parseArguments(arguments, toolInputSchemaMap.get(toolName));
                    } catch (Exception e) {
                        log.error("Failed to parse tool arguments for tool {}: {}", toolName, toolCall.getFunction().getArguments(), e);
                        String errorMsg = "Failed to parse arguments for tool: " + toolName + ". Error: " + e.getMessage() + ". The arguments may contain unescaped quotes or invalid JSON format. Please ensure the LLM generates properly escaped JSON.";
                        reportStep(StepEventStatus.failed, errorMsg, sseEmitterOpt);
                        
                        // 添加观察结果，告知 Agent 需要修正参数格式
                        observation = "Error: Failed to parse tool arguments. The JSON format is invalid. " +
                                "Common issue: unescaped quotes in string values. " +
                                "Example: \"print(\"Hello\")\" should be escaped as \"print(\\\"Hello\\\")\". " +
                                "Please retry with properly escaped JSON arguments.";
                        observations.add(observation);
                        continue;
                    }

                    log.info("round: {}, tool call: {}, input: {}", round, toolName, JSON.toJSONString(toolInput));

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
                    
                    // Update AgentContext based on tool type
                    if (toolName.startsWith("browser")) {
                        this.context.updateBrowserContext(toolName, observation);
                    } else if (toolName.startsWith("shell")) {
                        this.context.updateShellContext(toolName, observation);
                    } else if (toolName.startsWith("file")) {
                        String filePath = toolInput.containsKey("file") ? String.valueOf(toolInput.get("file")) : null;
                        this.context.updateFileContext(toolName, observation, filePath);
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
        if (frontendConnected.get() && sseEmitter != null) {
            executor.submit(() -> {
                if (frontendConnected.get()) {
                    sendOrForwardMessage(sseEmitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEvent));
                }
            });
        } else {
            logSseEvent(SSEEventType.MESSAGE.getType(), messageEvent);
        }
    }

    private void syncRespondThought(String reasoningContent, SseEmitter sseEmitter) {
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setReasoningContentDelta(reasoningContent);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && sseEmitter != null) {
            if (frontendConnected.get()) {
                sendOrForwardMessage(sseEmitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEvent));
            }
        } else {
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
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setContentDelta(content);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && sseEmitter != null) {
            if (frontendConnected.get()) {
                sendOrForwardMessage(sseEmitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEvent));
            }
        } else {
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
            stepData.setResult(step.getResult());
            stepData.setTimestamp(System.currentTimeMillis());
            return stepData;
        }).collect(Collectors.toList()));

        if (frontendConnected.get() && sseEmitter != null) {
            sendOrForwardMessage(sseEmitter, SSEEventType.PLAN.getType(), eventData);
        } else {
            logSseEvent(SSEEventType.MESSAGE.getType(), eventData);
        }
    }

    private void asyncStep(StepEventStatus status, String description, SseEmitter sseEmitter) {
        StepEventData data = new StepEventData();
        data.setTimestamp(System.currentTimeMillis());
        data.setStatus(status.getCode());
        data.setDescription(description);

        if (frontendConnected.get() && sseEmitter != null) {
            executor.submit(() -> {
                if (frontendConnected.get()) {
                    sendOrForwardMessage(sseEmitter, SSEEventType.STEP.getType(), data);
                }
            });
        } else {
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
            executor.submit(() -> {
                if (frontendConnected.get()) {
                    sendOrForwardMessage(sseEmitter, SSEEventType.TOOL.getType(), data);
                }
            });
        } else {
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
            executor.submit(() -> {
                if (frontendConnected.get()) {
                    sendOrForwardMessage(sseEmitterOpt, SSEEventType.TOOL.getType(), toolEventData);
                }
            });
        } else {
            logSseEvent(SSEEventType.TOOL.getType(), toolEventData);
        }
        this.context.addTool(toolEventData.getFunction());
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

    public String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // 过滤掉回环接口和未启用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    // 过滤掉 IPv6 地址和回环地址
                    if (!address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }
}