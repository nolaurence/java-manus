package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.dto.ConversationResponse;
import cn.nolaurene.cms.common.dto.skill.SkillDefinitionDTO;
import cn.nolaurene.cms.common.dto.skill.SkillExecutionRequest;
import cn.nolaurene.cms.common.dto.skill.SkillExecutionResult;
import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMemory;
import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.*;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillExecutionEngine;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillFileStorageService;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillToolProvider;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotAgentEvent;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotAgentLoop;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotLoopConfig;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotLoopResult;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotSkillDescriptor;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotSkillLoader;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotSkillCatalog;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotToolAdapter;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotToolDefinition;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotToolResult;
import cn.nolaurene.cms.service.sandbox.backend.utils.ReActParser;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.SseMessageForwardService;
import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.entity.ConversationInfoDO;
import cn.nolaurene.cms.dal.entity.AgentSessionServerDO;
import cn.nolaurene.cms.service.AgentSessionServerService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.loadPrompt;
import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.render;


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

    private ToolRegistry tools;
    @Setter
    @Getter
    private String systemPrompt;
    private ChatModel chatModel;
    private Agent agent;
    private final ChatMemory memory = new ChatMemory();
    private static final String START_SIGNAL = "[START]";
    private static final String DONE_SIGNAL = "[DONE]";
    private static final String THINK_TOOL_NAME = "dummy-server-think";
    private static final int MCP_TOOL_RETRY_TIMES = 3;
    private static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 1_048_576;
    private static final int RESERVE_TOKENS = 16_384;
    private static final int KEEP_RECENT_TOKENS = 20_000;
    private static final int CONTEXT_COMPACT_THRESHOLD_PERCENT = 90;
    private static final int COMPACTED_CONTEXT_MAX_CHARS = 60_000;
    private static final String COPILOT_TRANSCRIPT_MARKER = "copilotTranscript";
    private static final String DEFAULT_CONVERSATION_ICON = "MessageSquare";
    private static final Set<String> ALLOWED_CONVERSATION_ICONS = Set.of(
            "MessageSquare", "Code2", "Globe", "Database", "FileText", "Terminal",
            "Search", "Settings", "Bot", "Bug", "Wrench", "Palette", "BarChart3",
            "Calendar", "Mail", "Image", "Video", "Shield", "Zap", "BookOpen",
            "Cloud", "Folder", "ClipboardList", "Sparkles"
    );
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            5,
            20,
            0L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>()
    );

    private final AtomicBoolean frontendConnected = new AtomicBoolean(true);
    private volatile SseEmitter currentSseEmitter = null;
    private final List<Long> currentStepToolIds = new ArrayList<>();
    private final Map<String, Long> copilotToolMessageIds = new HashMap<>();
    /** Canonical LangChain transcript retained for subsequent Copilot turns. */
    private final List<dev.langchain4j.data.message.ChatMessage> copilotTranscript = new ArrayList<>();
    /** Records written after the last persisted transcript snapshot. */
    private final List<dev.langchain4j.data.message.ChatMessage> copilotRecoveryMessages = new ArrayList<>();
    private volatile CopilotAgentLoop activeCopilotLoop;
    private volatile boolean lastCopilotRunAborted;
    private volatile boolean lastCopilotRunReachedLimit;
    /** Covers abort requests that arrive while the loop is still being built. */
    private final AtomicBoolean copilotAbortRequested = new AtomicBoolean(false);
    private final AtomicBoolean copilotRunActive = new AtomicBoolean(false);
    private final Object copilotAbortLock = new Object();

    private String localServerIp = "127.0.0.1";

    @Resource
    private ConversationHistoryService conversationHistoryService;
    private String conversationUserId = "anonymous";
    private String conversationSessionId = null;

    @Resource
    private SseMessageForwardService sseMessageForwardService;

    @Resource
    private AgentSessionServerService agentSessionServerService;

    @Resource
    private ExecutionSubAgent executionSubAgent;

    @Resource
    private SkillToolProvider skillToolProvider;

    @Resource
    private SkillExecutionEngine skillExecutionEngine;

    @Resource
    private SkillFileStorageService skillFileStorageService;

    @Resource
    private CopilotSkillCatalog copilotSkillCatalog;

    @org.springframework.beans.factory.annotation.Value("${copilot.loop.enabled:true}")
    private boolean copilotLoopEnabled = true;

    @org.springframework.beans.factory.annotation.Value("${copilot.loop.max-turns:30}")
    private int copilotMaxTurns = 30;

    @org.springframework.beans.factory.annotation.Value("${copilot.loop.tool-timeout-ms:300000}")
    private long copilotToolTimeoutMs = 300_000L;

    @org.springframework.beans.factory.annotation.Value("${copilot.loop.model-timeout-ms:300000}")
    private long copilotModelTimeoutMs = 300_000L;

    @org.springframework.beans.factory.annotation.Value("${copilot.loop.max-transcript-messages:1000}")
    private int copilotMaxTranscriptMessages = 1_000;

    @org.springframework.beans.factory.annotation.Value("${copilot.loop.allow-tools:true}")
    private boolean copilotAllowTools = true;

    @org.springframework.beans.factory.annotation.Value("${copilot.skills.disabled:}")
    private String copilotDisabledSkills = "";

    @org.springframework.beans.factory.annotation.Value("${copilot.skills.directories:}")
    private String copilotSkillDirectories = "";

    public AgentExecutor() {
        this.MAX_ROUNDS = 30;
        try {
            this.localServerIp = getLocalIpAddress();
        } catch (Exception e) {
            log.warn("获取本地IP失败，使用默认值127.0.0.1", e);
        }
    }

    public void initialize(ToolRegistry tools, ChatModel chatModel, Agent agent) {
        this.tools = tools;
        this.chatModel = chatModel;
        this.MAX_ROUNDS = agent.getMaxLoop();
        this.agent = agent;
        this.copilotTranscript.clear();
        this.copilotRecoveryMessages.clear();
        this.copilotToolMessageIds.clear();
        this.lastCopilotRunAborted = false;
        this.lastCopilotRunReachedLimit = false;
        synchronized (copilotAbortLock) {
            this.copilotAbortRequested.set(false);
            this.copilotRunActive.set(false);
        }
        this.conversationUserId = agent.getUserId();
        this.conversationSessionId = agent.getAgentId();
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

        return Objects.equals(localServerIp, serverInfo.getServerIp());
    }

    private void sendOrForwardMessage(SseEmitter emitter, String eventName, Object data) {
        SseEmitter activeEmitter = currentSseEmitter != null ? currentSseEmitter : emitter;
        if (frontendConnected.get() && activeEmitter != null) {
            sendDirectMessage(activeEmitter, eventName, data);
            return;
        }

        if (!shouldDirectSend(agent.getAgentId())) {
            AgentSessionServerDO serverInfo = agentSessionServerService.getByAgentId(agent.getAgentId());
            if (serverInfo != null) {
                log.info("转发消息到服务器 {}: agentId={}, eventName={}", serverInfo.getServerIp(), agent.getAgentId(), eventName);
                sseMessageForwardService.forwardMessage(serverInfo.getServerIp(), serverInfo.getServerPort(), agent.getAgentId(), eventName, data);
                return;
            }
        }

        log.warn("无法发送SSE消息: agentId={}, eventName={}, emitter={}, connected={}",
                agent.getAgentId(), eventName, activeEmitter != null, frontendConnected.get());
    }

    private void sendDirectMessage(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data)
                    .id(String.valueOf(System.currentTimeMillis())));
        } catch (Exception e) {
            log.error("直接发送SSE消息失败: agentId={}, eventName={}", agent.getAgentId(), eventName, e);
            if (frontendConnected.compareAndSet(true, false)) {
                log.info("发送错误，标记前端为断开连接");
            }
        }
    }

    public void planAct(String input, SseEmitter emitter) {
        this.lastCopilotRunAborted = false;
        this.lastCopilotRunReachedLimit = false;
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);

        setupSseEmitterListeners(emitter);
        ensureMemory();
        addMessageToMemory(new ChatMessage(ChatMessage.Role.user, input));

        AgentStatus agentStatus = AgentStatus.IDLE;
        Plan plan = new Plan();
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            try {
                switch (agentStatus) {
                    case IDLE:
                        log.info("[PLAN ACT] round {} start planning", round);
                        agentStatus = AgentStatus.PLANNING;
                        syncAgentStatusToConversationInfo(agentStatus);
                        break;

                    case PLANNING:
                        String rawPlan = agent.getPlanner().createPlan(chatModel, input, memory);

                        log.info("[PLAN ACT] Raw plan for round {}: {}", round, rawPlan);
                        if (StringUtils.isBlank(rawPlan)) {
                            log.warn("[PLAN ACT] No plan created for round {}, skipping to next round.", round);
                            agentStatus = AgentStatus.IDLE;
                            continue;
                        }
                        String thought = ReActParser.parseThinking(rawPlan);
                        if (StringUtils.isBlank(thought)) {
                            syncRespondThought(START_SIGNAL, emitter);
                            syncRespondThought(thought, emitter);
                            syncRespondThought(DONE_SIGNAL, emitter);
                        }
                        syncRespondContent(rawPlan, emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        addMessageToMemory(new ChatMessage(ChatMessage.Role.assistant, SSEEventType.MESSAGE,
                                StringUtils.isBlank(thought) ? rawPlan : "**Thinking:**\n" + thought + "\n\n**Response:**\n" + rawPlan));
                        plan = ReActParser.parsePlan(rawPlan);
                        if (null == plan) {
                            log.error("[PLAN ACT] Failed to parse plan for round {}, skipping to next round.", round);
                            continue;
                        }

                        plan.getSteps().forEach(step -> step.setStatus(StepEventStatus.pending.getCode()));
                        syncRespondPlan(plan, emitter);
                        addMessageToMemory(new ChatMessage(ChatMessage.Role.assistant, SSEEventType.PLAN, JSON.toJSONString(plan)));

                        // 写入 plan title 到 conversation_info
                        String icon = buildConversationBrief(plan.getTitle()).icon;
                        syncConversationInfo(plan.getTitle(), icon, AgentStatus.PLANNING);

                        // 发送 title SSE 事件给前端
                        TitleEventData titleEvent = new TitleEventData();
                        titleEvent.setTitle(plan.getTitle());
                        titleEvent.setIcon(icon);
                        titleEvent.setTimestamp(System.currentTimeMillis());
                        sendOrForwardMessage(emitter, SSEEventType.TITLE.getType(), titleEvent);

                        agentStatus = AgentStatus.EXECUTING;
                        syncAgentStatusToConversationInfo(agentStatus);
                        break;

                    case EXECUTING:
                        Optional<Step> currentStepOpt = plan.getSteps().stream()
                                .filter(step -> StepEventStatus.pending.getCode().equals(step.getStatus()))
                                .findFirst();
                        if (currentStepOpt.isEmpty()) {
                            log.info("[PLAN ACT] No pending steps in EXECUTING phase for round {}, go to CONCLUDING.", round);
                            agentStatus = AgentStatus.CONCLUDING;
                            break;
                        }

                        Step currentStep = currentStepOpt.get();
                        currentStep.setStatus(StepEventStatus.running.getCode());

                        reportStep(StepEventStatus.running, currentStep.getDescription(), emitter);
                        syncRespondPlan(plan, emitter);
                        addMessageToMemory(new ChatMessage(ChatMessage.Role.assistant, SSEEventType.PLAN, JSON.toJSONString(plan)));

                        List<Step> completedSteps = plan.getSteps()
                                .stream()
                                .filter(step -> StepEventStatus.completed.getCode().equals(step.getStatus()))
                                .collect(Collectors.toList());

                        if (CollectionUtils.isNotEmpty(completedSteps)) {
                            for (int idx = completedSteps.size() - 2; idx >= 0; idx--) {
                                completedSteps.get(idx).setResult("");
                            }
                        }

                        // Execute step via ExecutionSubAgent with native function calling
                        String observation = executionSubAgent.executeStepWithLoop(
                                chatModel,
                                agent.getExecutor(),
                                plan,
                                currentStep,
                                completedSteps,
                                agent.getExecutionMaxLoop(),
                                emitter,
                                agent);

                        currentStep.setResult(observation);
                        currentStep.setStatus(StepEventStatus.completed.getCode());

                        reportStep(StepEventStatus.completed, currentStep.getDescription(), emitter);
                        syncRespondPlan(plan, emitter);
                        conversationHistoryService.updateLastPlan(agent.getAgentId(), plan);

                        ensureMemory();
                        compactMemory();

                        boolean hasMorePendingSteps = plan.getSteps().stream()
                                .anyMatch(step -> StepEventStatus.pending.getCode().equals(step.getStatus()));
                        agentStatus = hasMorePendingSteps ? AgentStatus.UPDATING : AgentStatus.CONCLUDING;
                        syncAgentStatusToConversationInfo(agentStatus);
                        break;

                    case UPDATING:
                        List<Step> finishedSteps = plan.getSteps().stream().filter(step -> !step.getStatus().equals(StepEventStatus.pending.getCode())).collect(Collectors.toList());

                        String updatedStepsString = agent.getPlanner().updatePlan(chatModel, memory, plan);
                        log.info("[PLAN ACT] Updated steps for round {}: {}", round, updatedStepsString);

                        List<String> newSteps = ReActParser.parseStepDescriptions(updatedStepsString);
                        if (CollectionUtils.isEmpty(newSteps)) {
                            log.warn("[PLAN ACT] No new steps found in updated steps for round {}, skipping to conclude round.", round);
                            agentStatus = AgentStatus.CONCLUDING;
                            break;
                        }

                        finishedSteps.addAll(newSteps.stream().map(stepString -> {
                            Step step = new Step();
                            step.setDescription(stepString);
                            step.setStatus(StepEventStatus.pending.getCode());
                            return step;
                        }).collect(Collectors.toList()));
                        plan.setSteps(new ArrayList<>(finishedSteps));
                        log.info("[PLAN ACT] Updated global steps for round {}: {}", round, JSON.toJSONString(plan.getSteps()));

                        syncRespondPlan(plan, emitter);
                        conversationHistoryService.updateLastPlan(agent.getAgentId(), plan);

                        agentStatus = AgentStatus.EXECUTING;
                        syncAgentStatusToConversationInfo(agentStatus);
                        break;

                    case CONCLUDING:
                        String conclusion = agent.getExecutor().conclude(chatModel, memory.getHistory());

                        syncRespondThought(START_SIGNAL, emitter);
                        syncRespondThought(DONE_SIGNAL, emitter);
                        syncRespondContent(conclusion, emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        saveAssistantMessage(conclusion, SSEEventType.MESSAGE);
                        agentStatus = AgentStatus.IDLE;
                        syncAgentStatusToConversationInfo(AgentStatus.COMPLETED);
                        round = MAX_ROUNDS + 1;
                        break;

                    case COMPLETED:
                    default:
                        break;

                }
            } catch (RateLimitException e) {
                log.error("[PLAN ACT] Rate limit reached for round: {}, error: ", round, e);
                syncRespondContent("TPM到达上限了，请稍后再试。", emitter);
                syncRespondContent(DONE_SIGNAL, emitter);
                syncAgentStatusToConversationInfo(AgentStatus.IDLE);
                break;
            } catch (Exception e) {
                log.error("[PLAN ACT] Error when creating plan for round: {}, error: ", round, e);
                // Check if caused by RateLimitException
                Throwable cause = e.getCause();
                while (cause != null) {
                    if (cause instanceof RateLimitException) {
                        syncRespondContent("TPM到达上限了，请稍后再试。", emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        syncAgentStatusToConversationInfo(AgentStatus.IDLE);
                        return;
                    }
                    cause = cause.getCause();
                }
                syncAgentStatusToConversationInfo(AgentStatus.IDLE);
                break;
            }
        }
    }

    /**
     * Run the in-process Copilot-style loop.  The old prompt-driven loop is
     * retained as an explicit fallback for deployments that disable the new
     * runtime, but is no longer the default path.
     */
    public void skillBasedAgentLoop(String input, SseEmitter emitter) {
        if (copilotLoopEnabled) {
            runCopilotAgentLoop(input, emitter);
            return;
        }
        legacySkillBasedAgentLoop(input, emitter);
    }

    /** Execute one turn through the in-process Copilot-compatible loop. */
    private void runCopilotAgentLoop(String input, SseEmitter emitter) {
        synchronized (copilotAbortLock) {
            copilotRunActive.set(true);
            copilotAbortRequested.set(false);
        }
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);
        this.lastCopilotRunAborted = false;
        this.lastCopilotRunReachedLimit = false;
        String stagedSkillRoot = null;
        CopilotToolRegistry copilotTools = null;
        try {
            setupSseEmitterListeners(emitter);
            ensureMemory();

            if (StringUtils.isBlank(input)) {
                sendCopilotDone(emitter);
                syncAgentStatusToConversationInfo(AgentStatus.COMPLETED);
                return;
            }

            // Title generation must not consume an extra model turn: the Copilot
            // loop owns every model call for this user request.
            ConversationBrief conversationBrief = new ConversationBrief(
                    buildConversationTitle(input), DEFAULT_CONVERSATION_ICON);
            syncConversationInfo(conversationBrief.title, conversationBrief.icon, AgentStatus.EXECUTING);
            sendTitleEvent(conversationBrief.title, conversationBrief.icon, emitter);
            saveUserMessage(input);

            copilotToolMessageIds.clear();
            copilotTools = buildCopilotToolRegistry();
            List<dev.langchain4j.data.message.ChatMessage> initialMessages =
                    buildCopilotInitialMessages();
            // Persist the user message above, but keep a non-persisting copy in
            // the in-memory transcript so the next request sees this turn. The
            // loop appends the same prompt to its private request transcript.
            rememberCopilotUserMessage(input);
            // Write a turn-start snapshot as soon as the user message is
            // durable. If the process dies before the first model response,
            // resume still has the new prompt instead of only the prior turn.
            copilotTranscript.clear();
            int initialTranscriptStart = initialMessages.isEmpty()
                    || !(initialMessages.get(0) instanceof SystemMessage) ? 0 : 1;
            copilotTranscript.addAll(initialMessages.subList(initialTranscriptStart, initialMessages.size()));
            copilotTranscript.add(UserMessage.from(input));
            saveCopilotTranscriptSnapshot();
            // The skill view is materialized while constructing the prompt and
            // kept alive until the model finishes so relative support files are
            // stable for tool handlers.
            stagedSkillRoot = lastCopilotSkillRoot;

            CopilotLoopConfig loopConfig = new CopilotLoopConfig()
                    .setMaxTurns(Math.max(1, copilotMaxTurns > 0 ? copilotMaxTurns : MAX_ROUNDS))
                    .setModelTimeoutMillis(Math.max(1L, copilotModelTimeoutMs))
                    .setToolTimeoutMillis(Math.max(1L, copilotToolTimeoutMs))
                    .setMaxTranscriptMessages(Math.max(4, copilotMaxTranscriptMessages))
                    .setInvocationContext(buildCopilotInvocationContext(stagedSkillRoot))
                    .setPermissionHandler((invocation, definition) ->
                            copilotAllowTools || definition.skipPermission()
                                    ? cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotPermissionDecision.ALLOW
                                    : cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotPermissionDecision.DENY);
            CopilotAgentLoop loop = new CopilotAgentLoop(
                    chatModel,
                    copilotTools,
                    loopConfig,
                    agent == null ? conversationSessionId : agent.getAgentId(),
                    event -> handleCopilotEvent(event, emitter));
            boolean abortBeforeRun;
            synchronized (copilotAbortLock) {
                activeCopilotLoop = loop;
                // An abort may arrive during tool/skill discovery before the
                // loop has a cancellation handle. Consume it immediately
                // after publishing the handle so no request is lost.
                abortBeforeRun = copilotAbortRequested.getAndSet(false);
            }
            if (abortBeforeRun) {
                loop.abort();
            }

            try {
                CopilotLoopResult result = loop.run(input, initialMessages);
                lastCopilotRunAborted = result.isAborted();
                lastCopilotRunReachedLimit = result.isReachedLimit();
                rememberCopilotTranscript(result.getMessages());
                saveCopilotTranscriptSnapshot();
                if (result.isAborted()) {
                    sendCopilotAborted(emitter);
                    syncAgentStatusToConversationInfo(AgentStatus.IDLE);
                    return;
                }
                if (result.isReachedLimit()) {
                    sendCopilotLimit(emitter, result.getFinalText());
                    syncAgentStatusToConversationInfo(AgentStatus.IDLE);
                    return;
                }
                String finalText = result.getFinalText();
                if (StringUtils.isBlank(finalText)) {
                    finalText = "Task completed.";
                    syncRespondContent(finalText, emitter);
                    saveAssistantMessage(finalText, SSEEventType.MESSAGE);
                }
                sendCopilotDone(emitter);
                syncAgentStatusToConversationInfo(AgentStatus.COMPLETED);
            } catch (CopilotAgentLoop.CopilotAgentLoopException error) {
                rememberCopilotTranscript(error.getMessages());
                saveCopilotTranscriptSnapshot();
                throw error;
            }
        } catch (Exception error) {
            log.error("[COPILOT LOOP] execution failed", error);
            ErrorEventData errorData = new ErrorEventData();
            errorData.setTimestamp(System.currentTimeMillis());
            errorData.setError("执行失败: " + StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName()));
            sendOrForwardMessage(emitter, SSEEventType.ERROR.getType(), errorData);
            syncAgentStatusToConversationInfo(AgentStatus.IDLE);
            // Do not swallow the failure.  AgentSession must observe the
            // exception and mark the session FAILED instead of COMPLETED.
            throw error instanceof RuntimeException
                    ? (RuntimeException) error
                    : new IllegalStateException("Copilot loop failed", error);
        } finally {
            String cleanupSkillRoot = stagedSkillRoot != null ? stagedSkillRoot : lastCopilotSkillRoot;
            Runnable skillCleanup = () -> {
                if (cleanupSkillRoot != null && skillFileStorageService != null) {
                    skillFileStorageService.deleteCopilotSkillDirectory(cleanupSkillRoot);
                }
            };
            if (copilotTools != null) {
                copilotTools.cancelOutstanding(
                        Math.min(Math.max(1L, copilotToolTimeoutMs), 5_000L), skillCleanup);
            } else {
                skillCleanup.run();
            }
            lastCopilotSkillRoot = null;
            synchronized (copilotAbortLock) {
                activeCopilotLoop = null;
                copilotRunActive.set(false);
                copilotAbortRequested.set(false);
            }
        }
    }

    /** Request cancellation of the currently running Copilot-style turn. */
    public void abortCopilotLoop() {
        CopilotAgentLoop loop;
        synchronized (copilotAbortLock) {
            if (!copilotRunActive.get()) {
                // A stale abort while idle must not cancel the next prompt.
                copilotAbortRequested.set(false);
                return;
            }
            copilotAbortRequested.set(true);
            loop = activeCopilotLoop;
        }
        if (loop != null) {
            loop.abort();
        }
    }

    public boolean wasLastCopilotRunAborted() {
        return lastCopilotRunAborted;
    }

    public boolean wasLastCopilotRunReachedLimit() {
        return lastCopilotRunReachedLimit;
    }

    private volatile String lastCopilotSkillRoot;

    private Map<String, Object> buildCopilotInvocationContext(String stagedSkillRoot) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("agentId", agent == null ? null : agent.getAgentId());
        context.put("userId", agent == null ? conversationUserId : agent.getUserId());
        context.put("sessionId", conversationSessionId);
        if (StringUtils.isNotBlank(stagedSkillRoot)) {
            context.put("workingDirectory", stagedSkillRoot);
        }
        return context;
    }

    private CopilotToolRegistry buildCopilotToolRegistry() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        if (tools != null) {
            for (CopilotToolDefinition definition : tools.toCopilotToolDefinitions()) {
                registry.registerIfAbsent(definition);
            }
        }
        if (agent != null && agent.getVanillaTools() != null) {
            for (cn.nolaurene.cms.service.sandbox.backend.tool.Tool tool : agent.getVanillaTools()) {
                if (tool != null) {
                    registry.registerIfAbsent(CopilotToolAdapter.fromTool(tool));
                }
            }
        }

        if (agent != null && agent.getToolSpecifications() != null) {
            for (ToolSpecification specification : agent.getToolSpecifications()) {
                if (specification == null || StringUtils.isBlank(specification.name())
                        || specification.name().startsWith(SkillToolProvider.SKILL_TOOL_PREFIX)
                        || (skillToolProvider != null && skillToolProvider.isSkillTool(specification.name()))) {
                    continue;
                }
                CopilotToolDefinition definition = CopilotToolAdapter.fromToolSpecificationWithInvocation(
                        specification,
                        invocation -> {
                            ToolExecutionRequest request = ToolExecutionRequest.builder()
                                    .id(StringUtils.defaultIfBlank(invocation.getToolCallId(), UUID.randomUUID().toString()))
                                    .name(specification.name())
                                    .arguments(JSON.toJSONString(invocation.getArguments() == null
                                            ? Map.of() : invocation.getArguments()))
                                    .build();
                            ToolExecutionRequest finalRequest = request;
                            if (specification.name().startsWith("shell_")) {
                                finalRequest = injectAgentIdForShellTool(request, agent.getAgentId());
                            }
                            return java.util.concurrent.CompletableFuture.completedFuture(
                                    executeMcpToolForCopilot(specification.name(), finalRequest));
                        });
                if (!registry.registerIfAbsent(definition)) {
                    log.warn("Ignoring duplicate Copilot tool definition: {}", specification.name());
                }
            }
        }
        return registry;
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildCopilotInitialMessages() throws IOException {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        String system = loadPrompt("prompts/system.jinja");
        StringBuilder prompt = new StringBuilder(system);
        prompt.append("\n\n<agent_loop>\n")
                .append("Continue by calling tools when more information or actions are needed. ")
                .append("After each tool result, reassess the task. Stop only when the answer is complete.\n")
                .append("Tool arguments must be JSON objects matching their schemas.\n")
                .append("</agent_loop>\n");

        String userId = agent == null ? conversationUserId : agent.getUserId();
        Long parsedUserId = parseUserId(userId);
        List<String> enabledSkillIds = skillToolProvider == null
                ? List.of()
                : skillToolProvider.getEnabledSkillIdsForUser(parsedUserId);
        Set<String> disabledSkills = parseCsv(copilotDisabledSkills);
        lastCopilotSkillRoot = null;
        if ((!enabledSkillIds.isEmpty() && skillFileStorageService != null)
                || StringUtils.isNotBlank(copilotSkillDirectories)) {
            try {
                List<java.nio.file.Path> skillRoots = new ArrayList<>();
                if (!enabledSkillIds.isEmpty() && skillFileStorageService != null && copilotSkillCatalog != null) {
                    CopilotSkillCatalog.Selection selection = copilotSkillCatalog.prepare(
                            parsedUserId, disabledSkills);
                    lastCopilotSkillRoot = selection.getRoot();
                    skillRoots.add(java.nio.file.Paths.get(lastCopilotSkillRoot));
                } else if (!enabledSkillIds.isEmpty() && skillFileStorageService != null) {
                    lastCopilotSkillRoot = skillFileStorageService.createCopilotSkillDirectory(enabledSkillIds);
                    skillRoots.add(java.nio.file.Paths.get(lastCopilotSkillRoot));
                }
                for (String configuredDirectory : parseCsv(copilotSkillDirectories)) {
                    skillRoots.add(java.nio.file.Paths.get(configuredDirectory));
                }
                List<CopilotSkillDescriptor> skills = new CopilotSkillLoader(skillRoots, disabledSkills).load();
                if (!skills.isEmpty()) {
                    prompt.append("\n<skills>\n");
                    for (CopilotSkillDescriptor skill : skills) {
                        prompt.append("<skill name=\"")
                                .append(escapeXml(skill.name()))
                                .append("\" description=\"")
                                .append(escapeXml(skill.description()))
                                .append("\">\n")
                                .append(skill.body())
                                .append("\n</skill>\n");
                    }
                    prompt.append("</skills>\n");
                }
            } catch (Exception error) {
                log.warn("[COPILOT LOOP] failed to load skills", error);
            }
        }
        messages.add(SystemMessage.from(prompt.toString()));
        if (copilotTranscript.isEmpty()) {
            // Legacy history contains UI-only TOOL/STEP/PLAN records.  They
            // are not Copilot protocol messages and cannot be replayed as
            // assistant/tool pairs, so only ordinary user/assistant messages
            // (plus compaction summaries) enter a fresh Copilot transcript.
            messages.addAll(buildCopilotHistoryMessages());
        } else {
            messages.addAll(copilotTranscript);
            messages.addAll(copilotRecoveryMessages);
        }
        return messages;
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildCopilotHistoryMessages() {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        for (ChatMessage message : memory.getHistory()) {
            if (message == null || message.getRole() == ChatMessage.Role.tool) {
                continue;
            }
            SSEEventType eventType = message.getEventType();
            if (eventType != null
                    && eventType != SSEEventType.MESSAGE
                    && eventType != SSEEventType.COMPACT) {
                continue;
            }
            // Reasoning is streamed as a UI event and is persisted separately;
            // replaying it as a normal assistant turn duplicates context.
            if (message.getRole() == ChatMessage.Role.assistant
                    && StringUtils.startsWith(message.getContent(), "**Deep Thinking:**")) {
                continue;
            }
            messages.add(message.toLangchain4j());
        }
        return messages;
    }

    private void handleCopilotEvent(CopilotAgentEvent event, SseEmitter emitter) {
        if (event == null) return;
        switch (event.getType()) {
            case ASSISTANT_REASONING:
                syncRespondReasoning(event.getContent(), emitter);
                break;
            case ASSISTANT_MESSAGE_DELTA:
                syncRespondContent(event.getContent(), emitter);
                break;
            case ASSISTANT_MESSAGE:
                if (StringUtils.isNotBlank(event.getContent())) {
                    saveAssistantMessage(event.getContent(), SSEEventType.MESSAGE);
                }
                break;
            case TOOL_EXECUTION_START:
                String args = JSON.toJSONString(event.getArguments() == null ? Map.of() : event.getArguments());
                Long id = reportToolEvent(event.getToolName(), args, emitter);
                if (id != null && event.getToolCallId() != null) {
                    copilotToolMessageIds.put(event.getToolCallId(), id);
                }
                break;
            case TOOL_PERMISSION_REQUEST:
                sendOrForwardMessage(emitter, event.getType().getWireName(), event.toMap());
                break;
            case TOOL_EXECUTION_COMPLETE:
                Long messageId = copilotToolMessageIds.remove(event.getToolCallId());
                Object modelText = event.getData().get("modelText");
                String result = modelText != null
                        ? String.valueOf(modelText)
                        : event.getError() != null
                        ? "Tool error: " + event.getError()
                        : String.valueOf(event.getResult() == null ? "" : event.getResult());
                if (messageId != null && conversationHistoryService != null) {
                    conversationHistoryService.updateToolResult(messageId, result);
                }
                break;
            case SESSION_ERROR:
                ErrorEventData errorData = new ErrorEventData();
                errorData.setTimestamp(System.currentTimeMillis());
                errorData.setError(event.getError());
                sendOrForwardMessage(emitter, SSEEventType.ERROR.getType(), errorData);
                break;
            case SESSION_LIMIT:
                sendOrForwardMessage(emitter, event.getType().getWireName(), event.toMap());
                break;
            default:
                break;
        }
    }

    private void sendCopilotDone(SseEmitter emitter) {
        DoneEventData done = new DoneEventData();
        done.setTimestamp(System.currentTimeMillis());
        sendOrForwardMessage(emitter, SSEEventType.DONE.getType(), done);
    }

    private void sendCopilotAborted(SseEmitter emitter) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("aborted", true);
        sendOrForwardMessage(emitter, "ABORTED", data);
    }

    private void sendCopilotLimit(SseEmitter emitter, String finalText) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("limit", true);
        if (StringUtils.isNotBlank(finalText)) {
            data.put("message", finalText);
        }
        sendOrForwardMessage(emitter, "LIMIT_REACHED", data);
    }

    private void legacySkillBasedAgentLoop(String input, SseEmitter emitter) {
        this.lastCopilotRunAborted = false;
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);

        setupSseEmitterListeners(emitter);
        ensureMemory();

        if (StringUtils.isBlank(input)) {
            syncRespondContent(DONE_SIGNAL, emitter);
            syncAgentStatusToConversationInfo(AgentStatus.COMPLETED);
            return;
        }

        addMessageToMemory(new ChatMessage(ChatMessage.Role.user, input));
        ConversationBrief conversationBrief = buildConversationBrief(input);
        syncConversationInfo(conversationBrief.title, conversationBrief.icon, AgentStatus.EXECUTING);
        sendTitleEvent(conversationBrief.title, conversationBrief.icon, emitter);

        try {
            List<dev.langchain4j.data.message.ChatMessage> messages = buildSkillBasedInitialMessages();
            List<ToolSpecification> toolSpecs = buildSkillBasedToolSpecs();

            String finalResult = "";
            for (int round = 1; round <= MAX_ROUNDS; round++) {
                log.info("[SKILL LOOP] round {}/{} start, messages={}, tools={}",
                        round, MAX_ROUNDS, messages.size(), toolSpecs.size());

                ContextUsage contextUsage = calculateContextUsage(messages, toolSpecs);
                syncContextUsage(contextUsage, false, emitter);
                // Trigger compaction when used tokens exceed context window minus reserve (room for LLM response)
                if (contextUsage.usedTokens > DEFAULT_CONTEXT_WINDOW_TOKENS - RESERVE_TOKENS) {
                    log.info("[SKILL LOOP] context usage {}/{} tokens exceeded threshold (reserve={}), compacting messages",
                            contextUsage.usedTokens, DEFAULT_CONTEXT_WINDOW_TOKENS, RESERVE_TOKENS);
                    messages = compactAgentLoopMessages(messages, toolSpecs);
                    contextUsage = calculateContextUsage(messages, toolSpecs);
                    syncContextUsage(contextUsage, true, emitter);
                }

                dev.langchain4j.model.chat.request.ChatRequest request =
                        dev.langchain4j.model.chat.request.ChatRequest.builder()
                                .messages(messages)
                                .toolSpecifications(toolSpecs)
                                .build();

                ChatResponse response;
                try {
                    response = chatModel.chat(request);
                } catch (InvalidRequestException e) {
                    if (!isContextLengthExceeded(e)) {
                        throw e;
                    }
                    log.warn("[SKILL LOOP] context length exceeded, compacting and retrying once: {}", e.getMessage());
                    messages = compactAgentLoopMessages(messages, toolSpecs);
                    syncContextUsage(calculateContextUsage(messages, toolSpecs), true, emitter);
                    request = dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(messages)
                            .toolSpecifications(toolSpecs)
                            .build();
                    response = chatModel.chat(request);
                }
                AiMessage aiMessage = response.aiMessage();
                log.info("[LLM Response] round={} text={} thinking={} toolCalls={}",
                        round,
                        StringUtils.abbreviate(aiMessage.text(), 200),
                        StringUtils.abbreviate(aiMessage.thinking(), 200),
                        aiMessage.hasToolExecutionRequests() ? aiMessage.toolExecutionRequests().size() : 0);
                messages.add(aiMessage);
                syncRespondThinking(aiMessage, emitter);

                String aiText = aiMessage.text();
                if (StringUtils.isNotBlank(aiText)) {
                    finalResult = aiText;
                    syncRespondContent(aiText, emitter);
                    saveAssistantMessage(aiText, SSEEventType.MESSAGE);
                }

                if (!aiMessage.hasToolExecutionRequests()) {
                    log.info("[SKILL LOOP] no tool calls in round {}, finishing", round);
                    break;
                }

                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                log.info("[SKILL LOOP] round {} requested {} tool calls", round, toolRequests.size());
                for (ToolExecutionRequest toolRequest : toolRequests) {
                    ToolExecutionResultMessage toolResultMessage = executeAgentLoopTool(toolRequest, emitter);
                    messages.add(toolResultMessage);
                }

                if (round == MAX_ROUNDS) {
                    finalResult = StringUtils.defaultIfBlank(finalResult,
                            "Reached the maximum tool loop rounds before producing a final answer.");
                    log.warn("[SKILL LOOP] reached max rounds for agentId={}", agent.getAgentId());
                }
            }

            if (StringUtils.isBlank(finalResult)) {
                String fallback = "Task completed.";
                syncRespondContent(fallback, emitter);
                saveAssistantMessage(fallback, SSEEventType.MESSAGE);
            }
            syncRespondContent(DONE_SIGNAL, emitter);
            syncAgentStatusToConversationInfo(AgentStatus.COMPLETED);
        } catch (RateLimitException e) {
            log.error("[SKILL LOOP] Rate limit reached", e);
            syncRespondContent("TPM到达上限了，请稍后再试。", emitter);
            syncRespondContent(DONE_SIGNAL, emitter);
            syncAgentStatusToConversationInfo(AgentStatus.IDLE);
        } catch (Exception e) {
            log.error("[SKILL LOOP] execution failed", e);
            syncRespondContent("执行失败: " + e.getMessage(), emitter);
            syncRespondContent(DONE_SIGNAL, emitter);
            syncAgentStatusToConversationInfo(AgentStatus.IDLE);
        }
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildSkillBasedInitialMessages() throws IOException {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        String systemPrompt = loadPrompt("prompts/system.jinja");
        String loopPromptTemplate = loadPrompt("prompts/skillBasedAgentLoopSystem.jinja");
        Map<String, Object> context = new HashMap<>();
        context.put("toolSkill", loadPrompt("prompts/builtinSandboxMcpSkill.md"));
        context.put("availableSkills", buildAvailableImportedSkillsSection());

        messages.add(SystemMessage.from(systemPrompt + "\n\n" + render(loopPromptTemplate, context)));
        messages.addAll(memory.toLangchain4jMessages());
        return messages;
    }

    private List<ToolSpecification> buildSkillBasedToolSpecs() {
        List<ToolSpecification> mcpToolSpecs = agent.getToolSpecifications().stream()
                .filter(tool -> !tool.name().startsWith(SkillToolProvider.SKILL_TOOL_PREFIX))
                .collect(Collectors.toList());
        List<ToolSpecification> specs = new ArrayList<>(mcpToolSpecs);
        specs.add(ToolSpecification.builder()
                .name(THINK_TOOL_NAME)
                .description("Use this tool to briefly share useful reasoning or progress before continuing. It does not gather information or change state.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("thought", "Brief reasoning or progress update.")
                        .required("thought")
                        .build())
                .build());
        return specs;
    }

    private String buildAvailableImportedSkillsSection() {
        try {
            Set<String> enabledSkillIds = skillToolProvider.getSkillToolSpecificationsForUser(parseUserId(agent.getUserId()))
                    .stream()
                    .map(tool -> skillToolProvider.parseSkillIdFromToolName(tool.name()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<SkillDefinitionDTO> skills = enabledSkillIds.stream()
                    .map(skillToolProvider::getSkillDefinition)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(skills)) {
                return "(No imported skills are currently enabled.)";
            }

            String extractedPath = skillFileStorageService.getExtractedPath();
            StringBuilder sb = new StringBuilder();
            sb.append("<available_skills>\n");
            for (SkillDefinitionDTO skill : skills) {
                sb.append("  <skill>\n");
                sb.append("    <name>").append(escapeXml(StringUtils.defaultString(skill.getName()))).append("</name>\n");
                sb.append("    <skill_id>").append(escapeXml(StringUtils.defaultString(skill.getSkillId()))).append("</skill_id>\n");
                sb.append("    <description>").append(escapeXml(StringUtils.defaultString(skill.getDescription()))).append("</description>\n");
                sb.append("    <location>").append(escapeXml(extractedPath + "/" + skill.getSkillId() + "/SKILL.md")).append("</location>\n");
                sb.append("  </skill>\n");
            }
            sb.append("</available_skills>");
            return sb.toString();
        } catch (Exception e) {
            log.warn("[SKILL LOOP] failed to build imported skills section", e);
            return "(Imported skills could not be loaded for this turn.)";
        }
    }

    private ToolExecutionResultMessage executeAgentLoopTool(ToolExecutionRequest toolRequest, SseEmitter emitter) {
        String toolName = toolRequest.name();
        String arguments = toolRequest.arguments();
        log.info("[SKILL LOOP] tool call: {}, args={}", toolName, arguments);

        if (THINK_TOOL_NAME.equals(toolName)) {
            String thought = extractThought(arguments);
            if (StringUtils.isNotBlank(thought)) {
                syncRespondReasoning(thought, emitter);
            }
            return ToolExecutionResultMessage.from(toolRequest, "Thought logged.");
        }

        ToolExecutionRequest finalToolRequest = toolRequest;
        String finalArguments = arguments;
        if (toolName.startsWith("shell_")) {
            finalToolRequest = injectAgentIdForShellTool(toolRequest, agent.getAgentId());
            finalArguments = finalToolRequest.arguments();
        }

        Long toolMessageId = reportToolEvent(toolName, finalArguments, emitter);
        String observation = skillToolProvider.isSkillTool(toolName)
                ? executeSkillTool(toolName, finalToolRequest)
                : executeMcpToolWithRetry(toolName, finalToolRequest);
        log.info("[SKILL LOOP] tool {} result: {}", toolName, observation);
        if (toolMessageId != null) {
            conversationHistoryService.updateToolResult(
                    toolMessageId,
                    ToolObservationSummarizer.forPersistence(toolName, observation));
        }
        return ToolExecutionResultMessage.from(toolRequest, observation);
    }

    private String executeMcpToolWithRetry(String toolName, ToolExecutionRequest request) {
        return executeMcpToolOutcome(toolName, request).text;
    }

    /** Return a typed result for the Copilot registry so failures are not
     * mistaken for successful string results. */
    private CopilotToolResult executeMcpToolForCopilot(String toolName,
                                                       ToolExecutionRequest request) {
        McpToolOutcome outcome = executeMcpToolOutcome(toolName, request);
        return outcome.success
                ? CopilotToolResult.success(outcome.text)
                : CopilotToolResult.error(outcome.text);
    }

    private McpToolOutcome executeMcpToolOutcome(String toolName, ToolExecutionRequest request) {
        McpClient mcpClient = selectMcpClient(toolName);
        if (mcpClient == null) {
            return McpToolOutcome.failure("No MCP client available for tool: " + toolName);
        }

        Exception lastException = null;
        for (int i = 0; i < MCP_TOOL_RETRY_TIMES; i++) {
            try {
                ToolExecutionResult result = mcpClient.executeTool(request);
                String resultText = result == null || result.resultText() == null
                        ? "(empty result)" : result.resultText();
                if (result != null && result.isError()) {
                    return McpToolOutcome.failure(resultText);
                }
                return McpToolOutcome.success(resultText);
            } catch (Exception e) {
                lastException = e;
                log.warn("[SKILL LOOP] tool {} failed, attempt {}/{}: {}",
                        toolName, i + 1, MCP_TOOL_RETRY_TIMES, e.getMessage());
                if (i < MCP_TOOL_RETRY_TIMES - 1) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                            return McpToolOutcome.failure(
                                    "Tool execution interrupted: " + interruptedException.getMessage());
                    }
                }
            }
        }

        return McpToolOutcome.failure("Tool call error after retries: "
                + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    private static final class McpToolOutcome {
        private final boolean success;
        private final String text;

        private McpToolOutcome(boolean success, String text) {
            this.success = success;
            this.text = text == null ? "" : text;
        }

        private static McpToolOutcome success(String text) {
            return new McpToolOutcome(true, text);
        }

        private static McpToolOutcome failure(String text) {
            return new McpToolOutcome(false, text);
        }
    }

    private McpClient selectMcpClient(String toolName) {
        if (toolName != null && toolName.startsWith("browser")) {
            return agent.getBrowserMcpClient();
        }
        return agent.getNativeMcpClient();
    }

    private String executeSkillTool(String toolName, ToolExecutionRequest request) {
        String skillId = skillToolProvider.parseSkillIdFromToolName(toolName);
        if (skillId == null) {
            return "Cannot find Skill ID for tool: " + toolName;
        }

        try {
            JSONObject argsJson = JSON.parseObject(request.arguments());
            if (argsJson == null) {
                argsJson = new JSONObject();
            }

            SkillExecutionRequest skillRequest = new SkillExecutionRequest();
            skillRequest.setSkillId(skillId);
            skillRequest.setSessionId(StringUtils.defaultIfBlank(argsJson.getString("session_id"), agent.getAgentId()));
            skillRequest.setUserId(parseUserId(agent.getUserId()));
            skillRequest.setWorkingDir("");

            Map<String, Object> params = new HashMap<>();
            for (String key : argsJson.keySet()) {
                if (!"session_id".equals(key)) {
                    params.put(key, argsJson.get(key));
                }
            }
            skillRequest.setParams(params);

            SkillExecutionResult result = skillExecutionEngine.execute(skillRequest, agent.getNativeMcpClient());
            if ("SUCCESS".equals(result.getStatus())) {
                return result.getOutput() != null ? result.getOutput() : "(skill executed successfully)";
            }
            return "Skill execution failed: " + StringUtils.defaultIfBlank(result.getError(), "unknown error");
        } catch (Exception e) {
            log.error("[SKILL LOOP] failed to execute skill tool {}", toolName, e);
            return "Skill execution error: " + e.getMessage();
        }
    }

    private ToolExecutionRequest injectAgentIdForShellTool(ToolExecutionRequest originalRequest, String agentId) {
        try {
            JSONObject argsJson = JSON.parseObject(originalRequest.arguments());
            if (argsJson == null) {
                argsJson = new JSONObject();
            }
            argsJson.put("id", agentId);
            return ToolExecutionRequest.builder()
                    .id(originalRequest.id())
                    .name(originalRequest.name())
                    .arguments(argsJson.toJSONString())
                    .build();
        } catch (Exception e) {
            log.warn("[SKILL LOOP] failed to inject agentId for shell tool: {}", e.getMessage());
            return originalRequest;
        }
    }

    private String extractThought(String arguments) {
        try {
            JSONObject obj = JSON.parseObject(arguments);
            String thought = obj.getString("thought");
            return thought != null ? thought : arguments;
        } catch (Exception e) {
            return arguments;
        }
    }

    private void syncRespondThinking(AiMessage aiMessage, SseEmitter sseEmitter) {
        if (aiMessage == null || StringUtils.isBlank(aiMessage.thinking())) {
            return;
        }

        syncRespondReasoning(aiMessage.thinking(), sseEmitter);
    }

    private ContextUsage calculateContextUsage(List<dev.langchain4j.data.message.ChatMessage> messages,
                                               List<ToolSpecification> toolSpecs) {
        int usedTokens = 0;
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            usedTokens += estimateTokens(extractMessageText(message)) + 4;
        }
        for (ToolSpecification toolSpec : toolSpecs) {
            usedTokens += estimateTokens(toolSpec.name());
            usedTokens += estimateTokens(toolSpec.description());
            usedTokens += estimateTokens(toolSpec.parameters() == null ? "" : toolSpec.parameters().toString());
        }
        int percent = Math.min(100, (int) Math.ceil((usedTokens * 100.0) / DEFAULT_CONTEXT_WINDOW_TOKENS));
        return new ContextUsage(usedTokens, DEFAULT_CONTEXT_WINDOW_TOKENS, percent);
    }

    private List<dev.langchain4j.data.message.ChatMessage> compactAgentLoopMessages(
            List<dev.langchain4j.data.message.ChatMessage> messages,
            List<ToolSpecification> toolSpecs) {
        if (CollectionUtils.isEmpty(messages) || messages.size() <= 2) {
            return messages;
        }

        // 1. Walk backwards from the newest message, accumulating token estimates
        //    until KEEP_RECENT_TOKENS is reached. This mimics pi's cut-point logic.
        int accumulated = 0;
        int cutIndex = messages.size(); // default: keep everything

        for (int i = messages.size() - 1; i >= 1; i--) {
            dev.langchain4j.data.message.ChatMessage msg = messages.get(i);
            int msgTokens = estimateTokens(extractMessageText(msg)) + 4;

            if (accumulated + msgTokens > KEEP_RECENT_TOKENS && i > 1) {
                // Cut point found — but never cut on a ToolExecutionResultMessage
                // (tool results must stay paired with their tool call).
                if (msg instanceof ToolExecutionResultMessage) {
                    // Step back to before the tool call that produced this result
                    int j = i - 1;
                    while (j >= 1 && messages.get(j) instanceof ToolExecutionResultMessage) {
                        j--;
                    }
                    // j now points to the AiMessage (tool call) or earlier
                    // Step back one more so the tool call itself is also kept
                    if (j > 1) {
                        cutIndex = j;
                    } else {
                        cutIndex = 1; // keep everything after system prompt
                    }
                } else {
                    cutIndex = i;
                }
                break;
            }
            accumulated += msgTokens;
        }

        if (cutIndex <= 1) {
            // Nothing to compact — keep everything
            return messages;
        }

        // 2. Collect messages that will be summarized (skip system prompt at index 0)
        List<dev.langchain4j.data.message.ChatMessage> messagesToSummarize = new ArrayList<>();
        for (int i = 1; i < cutIndex; i++) {
            messagesToSummarize.add(messages.get(i));
        }

        // 3. Generate structured summary (pi-style)
        String summary = generateStructuredSummary(messagesToSummarize);

        // 4. Assemble compacted message list: system + summary + kept recent messages
        List<dev.langchain4j.data.message.ChatMessage> compacted = new ArrayList<>();
        compacted.add(messages.get(0)); // system prompt

        String compactedText = "## Context Summary\n"
                + "The previous conversation context was compacted to stay within the model's context window.\n"
                + "Keep working from this compacted summary:\n\n"
                + summary;
        compacted.add(UserMessage.from(truncateForCompaction(compactedText, COMPACTED_CONTEXT_MAX_CHARS)));

        for (int i = cutIndex; i < messages.size(); i++) {
            compacted.add(messages.get(i));
        }

        // 5. Persist compaction boundary to DB so ensureMemory() can reconstruct it
        saveCompactionEvent(summary, cutIndex, messages.size());

        log.info("[SKILL LOOP] Compacted {} messages -> {} messages (cut at index {}, kept {} recent tokens)",
                messages.size(), compacted.size(), cutIndex, accumulated);

        return compacted;
    }

    private String generateStructuredSummary(
            List<dev.langchain4j.data.message.ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();

        // Goal — from the first user message
        sb.append("## Goal\n");
        for (dev.langchain4j.data.message.ChatMessage msg : messages) {
            if (msg instanceof UserMessage) {
                sb.append(truncateForCompaction(extractMessageText(msg), 1_000)).append("\n");
                break;
            }
        }

        // Progress — tool calls, results, and assistant responses
        sb.append("\n## Progress\n");
        for (dev.langchain4j.data.message.ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage) {
                String text = truncateForCompaction(extractMessageText(msg), 500);
                sb.append("- [Tool Result] ").append(text).append("\n");
            } else if (msg instanceof AiMessage) {
                AiMessage ai = (AiMessage) msg;
                if (ai.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                        sb.append("- [Tool Call] ").append(req.name())
                          .append(" args=").append(truncateForCompaction(req.arguments(), 300)).append("\n");
                    }
                } else {
                    sb.append("- [Assistant] ").append(truncateForCompaction(extractMessageText(msg), 500)).append("\n");
                }
            }
        }

        sb.append("\n## Next Steps\n");
        sb.append("Continue from the current state.\n");

        return sb.toString();
    }

    private void saveCompactionEvent(String summary, int cutIndex, int totalMessages) {
        if (conversationHistoryService == null) {
            return;
        }
        try {
            // Persist only the human-readable summary text (not JSON metadata)
            // so that when restored, LLM sees a clean summary UserMessage.
            String compactText = "## Context Summary\n"
                    + "The previous conversation context was compacted to stay within the model's context window.\n"
                    + "Keep working from this compacted summary:\n\n"
                    + summary;

            saveAssistantEventWithoutMemory(compactText, SSEEventType.COMPACT);
        } catch (Exception e) {
            log.warn("failed to persist compaction event", e);
        }
    }

    private String truncateForCompaction(String text, int maxChars) {

        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n... (context truncated during compaction)";
    }

    private String extractMessageText(dev.langchain4j.data.message.ChatMessage message) {
        if (message == null) {
            return "";
        }
        if (message instanceof SystemMessage) {
            return ((SystemMessage) message).text();
        }
        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return userMessage.hasSingleText() ? userMessage.singleText() : userMessage.toString();
        }
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            String text = StringUtils.defaultString(aiMessage.text());
            if (StringUtils.isNotBlank(aiMessage.thinking())) {
                text += "\n" + aiMessage.thinking();
            }
            if (aiMessage.hasToolExecutionRequests()) {
                text += "\n" + aiMessage.toolExecutionRequests();
            }
            return text;
        }
        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolResultMessage = (ToolExecutionResultMessage) message;
            return toolResultMessage.toolName() + "\n" + toolResultMessage.text();
        }
        return message.toString();
    }

    private int estimateTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }

        int chineseCount = 0;
        int englishWordCount = 0;
        int otherCharCount = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseCount++;
                inWord = false;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                if (!inWord) {
                    englishWordCount++;
                    inWord = true;
                }
            } else if (Character.isWhitespace(c)) {
                inWord = false;
                otherCharCount++;
            } else {
                otherCharCount++;
                inWord = false;
            }
        }
        return (int) Math.ceil(chineseCount * 1.5 + englishWordCount * 1.3 + otherCharCount * 0.5);
    }

    private boolean isContextLengthExceeded(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("maximum context length")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void syncContextUsage(ContextUsage usage, boolean compacted, SseEmitter emitter) {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("usedTokens", usage.usedTokens);
        data.put("maxTokens", usage.maxTokens);
        data.put("percent", usage.percent);
        data.put("compacted", compacted);
        sendOrForwardMessage(emitter, SSEEventType.CONTEXT.getType(), data);
        saveAssistantEventWithoutMemory(JSON.toJSONString(data), SSEEventType.CONTEXT);
    }

    private static class ContextUsage {
        private final int usedTokens;
        private final int maxTokens;
        private final int percent;

        private ContextUsage(int usedTokens, int maxTokens, int percent) {
            this.usedTokens = usedTokens;
            this.maxTokens = maxTokens;
            this.percent = percent;
        }
    }

    private Long reportToolEvent(String toolName, String arguments, SseEmitter emitter) {
        ToolEventData toolEventData = new ToolEventData();
        toolEventData.setTimestamp(System.currentTimeMillis());
        toolEventData.setName(resolveToolType(toolName));
        toolEventData.setFunction(toolName);
        try {
            toolEventData.setArgs(JSON.parseObject(arguments, Map.class));
        } catch (Exception e) {
            Map<String, Object> fallbackArgs = new HashMap<>();
            fallbackArgs.put("raw", arguments);
            toolEventData.setArgs(fallbackArgs);
        }

        sendOrForwardMessage(emitter, SSEEventType.TOOL.getType(), toolEventData);
        Long toolMessageId = saveAssistantMessageWithId(JSON.toJSONString(toolEventData), SSEEventType.TOOL);
        if (toolMessageId != null) {
            currentStepToolIds.add(toolMessageId);
        }
        return toolMessageId;
    }

    private String resolveToolType(String toolName) {
        if (toolName == null) {
            return "tool";
        }
        if (toolName.startsWith("browser")) {
            return "browser";
        }
        if (toolName.startsWith("shell")) {
            return "shell";
        }
        if (toolName.startsWith("file")) {
            return "file";
        }
        return "tool";
    }

    private void sendTitleEvent(String title, String icon, SseEmitter emitter) {
        TitleEventData titleEvent = new TitleEventData();
        titleEvent.setTitle(title);
        titleEvent.setIcon(icon);
        titleEvent.setTimestamp(System.currentTimeMillis());
        sendOrForwardMessage(emitter, SSEEventType.TITLE.getType(), titleEvent);
    }

    private ConversationBrief buildConversationBrief(String input) {
        String fallbackTitle = buildConversationTitle(input);
        if (chatModel == null || StringUtils.isBlank(input)) {
            return new ConversationBrief(fallbackTitle, DEFAULT_CONVERSATION_ICON);
        }

        try {
            String iconCandidates = String.join(", ", ALLOWED_CONVERSATION_ICONS);
            String prompt = "Summarize this conversation starter into a short chat title and choose one lucide-react icon.\n"
                    + "Return strict JSON only, no markdown.\n"
                    + "Schema: {\"title\":\"short title under 40 chars\",\"icon\":\"one icon name\"}\n"
                    + "Allowed icon names: " + iconCandidates + "\n\n"
                    + "Conversation starter:\n" + StringUtils.abbreviate(input, 2_000);

            ChatResponse response = chatModel.chat(
                    dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(List.of(UserMessage.from(prompt)))
                            .build());

            String raw = response.aiMessage() == null ? "" : response.aiMessage().text();
            JSONObject json = JSON.parseObject(extractJsonObject(raw));
            String title = sanitizeConversationTitle(json.getString("title"), fallbackTitle);
            String icon = normalizeConversationIcon(json.getString("icon"));
            return new ConversationBrief(title, icon);
        } catch (Exception e) {
            log.warn("failed to build conversation title/icon, using fallback", e);
            return new ConversationBrief(fallbackTitle, DEFAULT_CONVERSATION_ICON);
        }
    }

    private String buildConversationTitle(String input) {
        String title = StringUtils.normalizeSpace(input);
        if (StringUtils.isBlank(title)) {
            return "New Chat";
        }
        return title.length() > 40 ? title.substring(0, 40) + "..." : title;
    }

    private String sanitizeConversationTitle(String title, String fallbackTitle) {
        String normalized = StringUtils.normalizeSpace(title);
        if (StringUtils.isBlank(normalized)) {
            return fallbackTitle;
        }
        return normalized.length() > 40 ? normalized.substring(0, 40) + "..." : normalized;
    }

    private String normalizeConversationIcon(String icon) {
        String normalized = StringUtils.trimToEmpty(icon);
        if (ALLOWED_CONVERSATION_ICONS.contains(normalized)) {
            return normalized;
        }
        return DEFAULT_CONVERSATION_ICON;
    }

    private String extractJsonObject(String raw) {
        String text = StringUtils.trimToEmpty(raw);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static class ConversationBrief {
        private final String title;
        private final String icon;

        private ConversationBrief(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private Long parseUserId(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            log.warn("[SKILL LOOP] invalid userId for skill loading: {}", userId);
            return null;
        }
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Set<String> parseCsv(String value) {
        if (StringUtils.isBlank(value)) {
            return Set.of();
        }
        String normalized = value.trim();
        // Spring may stringify a YAML list as "[a, b]" when this property is
        // bound to a String. Accept both that representation and CSV input.
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .map(item -> item.length() >= 2
                        && ((item.startsWith("\"") && item.endsWith("\""))
                        || (item.startsWith("'") && item.endsWith("'")))
                        ? item.substring(1, item.length() - 1) : item)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void resumeSseEmitter(SseEmitter emitter) {
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);
        setupSseEmitterListeners(emitter);
        log.info("AgentExecutor SSE emitter resumed: agentId={}", agent.getAgentId());
    }

    private void syncRespondThought(String reasoningContent, SseEmitter sseEmitter) {
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setReasoningContentDelta(reasoningContent);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && currentSseEmitter != null) {
            sendOrForwardMessage(sseEmitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEvent));
        } else {
            logSseEvent(SSEEventType.MESSAGE.getType(), messageEvent);
        }
    }

    private void syncRespondReasoning(String reasoningContent, SseEmitter sseEmitter) {
        if (StringUtils.isBlank(reasoningContent)) {
            return;
        }

        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setReasoningContent(reasoningContent);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && currentSseEmitter != null) {
            sendOrForwardMessage(sseEmitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEvent));
        } else {
            logSseEvent(SSEEventType.MESSAGE.getType(), messageEvent);
        }
        saveAssistantEventWithoutMemory("**Deep Thinking:**\n" + reasoningContent, SSEEventType.MESSAGE);
    }

    private void syncRespondContent(String content, SseEmitter sseEmitter) {
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setContentDelta(content);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && currentSseEmitter != null) {
            sendOrForwardMessage(sseEmitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEvent));
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

        if (frontendConnected.get() && currentSseEmitter != null) {
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

        if (frontendConnected.get() && currentSseEmitter != null) {
            executor.submit(() -> {
                if (frontendConnected.get()) {
                    sendOrForwardMessage(sseEmitter, SSEEventType.STEP.getType(), data);
                }
            });
        } else {
            logSseEvent(SSEEventType.STEP.getType(), data);
        }
    }

    private void reportStep(StepEventStatus status, String description, SseEmitter sseEmitterOpt) {
        this.memory.add(new ChatMessage(ChatMessage.Role.assistant, SSEEventType.STEP, description));
        if (frontendConnected.get() && sseEmitterOpt != null) {
            asyncStep(status, description, sseEmitterOpt);
        } else {
            StepEventData data = new StepEventData();
            data.setTimestamp(System.currentTimeMillis());
            data.setStatus(status.getCode());
            data.setDescription(description);
            logSseEvent(SSEEventType.STEP.getType(), data);
        }

        switch(status) {
            case running:
                currentStepToolIds.clear();
                conversationHistoryService.addStep(agent.getUserId(), agent.getAgentId(), description);
                break;
            case completed:
                conversationHistoryService.updateLastStepStatus(agent.getAgentId(), StepEventStatus.completed.getCode(), new ArrayList<>(currentStepToolIds));
                break;
            case failed:
                conversationHistoryService.updateLastStepStatus(agent.getAgentId(), StepEventStatus.failed.getCode(), null);
                break;
        }
    }

    private void logSseEvent(String eventType, Object eventData) {
        if (log.isDebugEnabled()) {
            log.debug("BG Event - Type: {}, Data: {}", eventType, JSON.toJSONString(eventData));
        }
    }

    private void syncConversationInfo(String title, AgentStatus status) {
        syncConversationInfo(title, null, status);
    }

    private void syncConversationInfo(String title, String icon, AgentStatus status) {
        if (conversationHistoryService == null || conversationSessionId == null) return;
        try {
            ConversationInfoDO info = new ConversationInfoDO();
            info.setSessionId(conversationSessionId);
            // 优先使用 agent 中的 userId，如果为空则使用 conversationUserId
            String userId = (agent != null && StringUtils.isNotBlank(agent.getUserId())) ? agent.getUserId() : conversationUserId;
            info.setUserId(userId);
            if (title != null) {
                info.setTitle(title);
            }
            if (icon != null) {
                info.setIcon(icon);
            }
            if (status != null) {
                info.setStatus(status.getCode());
            }
            conversationHistoryService.upsertConversationInfo(info);
        } catch (Exception e) {
            log.warn("failed to sync conversation info", e);
        }
    }

    private void syncAgentStatusToConversationInfo(AgentStatus status) {
        if (conversationHistoryService == null || conversationSessionId == null) return;
        try {
            ConversationInfoDO info = new ConversationInfoDO();
            info.setSessionId(conversationSessionId);
            info.setStatus(status.getCode());
            conversationHistoryService.upsertConversationInfo(info);
        } catch (Exception e) {
            log.warn("failed to sync agent status to conversation info", e);
        }
    }

    private void setupSseEmitterListeners(SseEmitter sseEmitter) {
        sseEmitter.onCompletion(() -> {
            log.info("SSE connection completed/onCompletion (user likely left)");
            if (currentSseEmitter == sseEmitter && frontendConnected.compareAndSet(true, false)) {
                log.info("Frontend connection marked as disconnected via onCompletion.");
            }
        });
        sseEmitter.onError((Throwable t) -> {
            log.warn("SSE connection encountered error/onError", t);
            if (currentSseEmitter == sseEmitter && frontendConnected.compareAndSet(true, false)) {
                log.info("Frontend connection marked as disconnected via onError.");
            }
        });
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
        saveAssistantMessageWithId(content, eventType);
    }

    private Long saveAssistantMessageWithId(String content, SSEEventType eventType) {
        // Keep the runtime transcript correct even when persistence is
        // disabled in an embedded/test deployment.
        memory.add(new ChatMessage(ChatMessage.Role.assistant, eventType, content));
        if (conversationHistoryService == null) {
            return null;
        }
        try {
            ConversationRequest req = new ConversationRequest();
            req.setUserId(conversationUserId);
            req.setSessionId(conversationSessionId != null ? conversationSessionId : agent.getAgentId());
            req.setMessageType(ConversationHistoryDO.MessageType.ASSISTANT);
            req.setEventType(eventType);
            req.setContent(content);
            req.setMetadata(null);
            ConversationResponse response = conversationHistoryService.saveConversation(req);
            return response.getId();
        } catch (Exception e) {
            log.warn("failed to persist assistant message", e);
            return null;
        }
    }

    private Long saveAssistantEventWithoutMemory(String content, SSEEventType eventType) {
        if (conversationHistoryService == null) {
            return null;
        }
        try {
            ConversationRequest req = new ConversationRequest();
            req.setUserId(conversationUserId);
            req.setSessionId(conversationSessionId != null ? conversationSessionId : agent.getAgentId());
            req.setMessageType(ConversationHistoryDO.MessageType.ASSISTANT);
            req.setEventType(eventType);
            req.setContent(content);
            req.setMetadata(null);
            ConversationResponse response = conversationHistoryService.saveConversation(req);
            return response.getId();
        } catch (Exception e) {
            log.warn("failed to persist assistant event", e);
            return null;
        }
    }

    public String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("获取本地IP失败，使用默认值127.0.0.1", e);
            return "127.0.0.1";
        }
    }

    private void ensureMemory() {
        if (memory.isEmpty() && conversationHistoryService != null && agent != null) {
            copilotRecoveryMessages.clear();
            List<ConversationResponse> sessionConversations =
                    conversationHistoryService.getSessionConversationsForReplay(agent.getAgentId());
            // Keep compatibility with embedded hosts/mocks that only expose
            // the historical conversation lookup method.
            if (sessionConversations == null) {
                sessionConversations = conversationHistoryService.getSessionConversations(agent.getAgentId());
            }
            if (sessionConversations == null) {
                sessionConversations = List.of();
            }
            int restoreStartIndex = findLatestCompactIndex(sessionConversations);
            int transcriptSnapshotIndex = findLatestCopilotTranscriptIndex(sessionConversations);
            if (transcriptSnapshotIndex >= 0) {
                List<dev.langchain4j.data.message.ChatMessage> restored =
                        deserializeCopilotTranscript(sessionConversations.get(transcriptSnapshotIndex).getContent());
                if (!restored.isEmpty()) {
                    copilotTranscript.clear();
                    copilotTranscript.addAll(restored);
                    restoreStartIndex = Math.max(restoreStartIndex, transcriptSnapshotIndex + 1);
                }
            }

            for (int i = restoreStartIndex; i < sessionConversations.size(); i++) {
                ConversationResponse conversation = sessionConversations.get(i);
                if (conversation.getEventType() == SSEEventType.CONTEXT
                        || isCopilotTranscriptSnapshot(conversation)) {
                    continue;
                }
                switch(conversation.getMessageType()) {
                    case USER:
                        String userContent = normalizedConversationContent(conversation.getContent());
                        memory.add(new ChatMessage(ChatMessage.Role.user, conversation.getEventType(), userContent));
                        if (transcriptSnapshotIndex >= 0 && i > transcriptSnapshotIndex
                                && isCopilotRecoveryMessage(conversation)
                                && StringUtils.isNotBlank(userContent)) {
                            copilotRecoveryMessages.add(UserMessage.from(userContent));
                        }
                        break;
                    case ASSISTANT:
                        String assistantContent = normalizedConversationContent(conversation.getContent());
                        memory.add(new ChatMessage(ChatMessage.Role.assistant, conversation.getEventType(), assistantContent));
                        // A persisted assistant UI event contains only rendered
                        // text. It does not carry the model's tool-call IDs or
                        // arguments, so replaying it after a turn-start
                        // snapshot could create an invalid assistant/tool pair.
                        // The in-flight user prompt remains in the snapshot and
                        // will be replayed from that safe boundary instead.
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * Only records that can be represented as a plain Copilot transcript
     * message may be recovered after a snapshot.  TOOL records are UI audit
     * events whose JSON payload does not contain the assistant tool request;
     * replaying them as AiMessage would produce an invalid provider history.
     */
    static boolean isCopilotRecoveryMessage(ConversationResponse conversation) {
        if (conversation == null) {
            return false;
        }
        SSEEventType eventType = conversation.getEventType();
        if (eventType != null
                && eventType != SSEEventType.MESSAGE
                && eventType != SSEEventType.COMPACT) {
            return false;
        }
        return conversation.getMessageType() != ConversationHistoryDO.MessageType.ASSISTANT;
    }

    private static String normalizedConversationContent(Object content) {
        if (content == null) {
            return "";
        }
        return content instanceof String ? (String) content : JSON.toJSONString(content);
    }

    private int findLatestCompactIndex(List<ConversationResponse> conversations) {
        for (int i = conversations.size() - 1; i >= 0; i--) {
            if (conversations.get(i).getEventType() == SSEEventType.COMPACT) {
                return i;
            }
        }
        return 0;
    }

    private void addMessageToMemory(ChatMessage message) {
        switch(message.getRole()) {
            case user:
                saveUserMessage(message.getContent());
                this.memory.add(message);
                break;
            case assistant:
                saveAssistantMessage(message.getContent(), message.getEventType());
                break;
            default:
                break;
        }
    }

    private void rememberCopilotUserMessage(String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        List<ChatMessage> history = memory.getHistory();
        if (!history.isEmpty()) {
            ChatMessage last = history.get(history.size() - 1);
            if (last.getRole() == ChatMessage.Role.user
                    && Objects.equals(last.getContent(), content)) {
                return;
            }
        }
        memory.add(new ChatMessage(ChatMessage.Role.user, SSEEventType.MESSAGE, content));
    }

    private void rememberCopilotTranscript(
            List<dev.langchain4j.data.message.ChatMessage> messages) {
        copilotTranscript.clear();
        copilotRecoveryMessages.clear();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        int start = messages.get(0) instanceof SystemMessage ? 1 : 0;
        copilotTranscript.addAll(messages.subList(start, messages.size()));
    }

    /** Persist the normalized text/tool-call transcript for restart-safe resume. */
    private void saveCopilotTranscriptSnapshot() {
        if (conversationHistoryService == null || agent == null || copilotTranscript.isEmpty()) {
            return;
        }
        try {
            ConversationRequest request = new ConversationRequest();
            request.setUserId(conversationUserId);
            request.setSessionId(conversationSessionId != null ? conversationSessionId : agent.getAgentId());
            request.setMessageType(ConversationHistoryDO.MessageType.ASSISTANT);
            request.setEventType(SSEEventType.CONTEXT);
            request.setContent(serializeCopilotTranscript(copilotTranscript));
            request.setMetadata(JSON.toJSONString(Map.of(COPILOT_TRANSCRIPT_MARKER, true, "version", 1)));
            conversationHistoryService.saveConversation(request);
        } catch (Exception error) {
            log.warn("failed to persist Copilot transcript snapshot", error);
        }
    }

    private int findLatestCopilotTranscriptIndex(List<ConversationResponse> conversations) {
        if (conversations == null) {
            return -1;
        }
        for (int index = conversations.size() - 1; index >= 0; index--) {
            if (isCopilotTranscriptSnapshot(conversations.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isCopilotTranscriptSnapshot(ConversationResponse conversation) {
        if (conversation == null || conversation.getEventType() != SSEEventType.CONTEXT
                || StringUtils.isBlank(conversation.getMetadata())) {
            return false;
        }
        try {
            JSONObject metadata = JSON.parseObject(conversation.getMetadata());
            return metadata != null && metadata.getBooleanValue(COPILOT_TRANSCRIPT_MARKER);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String serializeCopilotTranscript(
            List<dev.langchain4j.data.message.ChatMessage> messages) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            Map<String, Object> record = new LinkedHashMap<>();
            if (message instanceof UserMessage) {
                record.put("role", "user");
                UserMessage user = (UserMessage) message;
                record.put("content", user.hasSingleText() ? user.singleText() : user.toString());
            } else if (message instanceof AiMessage) {
                AiMessage assistant = (AiMessage) message;
                record.put("role", "assistant");
                record.put("content", StringUtils.defaultString(assistant.text()));
                record.put("thinking", StringUtils.defaultString(assistant.thinking()));
                List<Map<String, Object>> calls = new ArrayList<>();
                if (assistant.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest request : assistant.toolExecutionRequests()) {
                        Map<String, Object> call = new LinkedHashMap<>();
                        call.put("id", request.id());
                        call.put("name", request.name());
                        call.put("arguments", request.arguments());
                        calls.add(call);
                    }
                }
                record.put("toolRequests", calls);
            } else if (message instanceof ToolExecutionResultMessage) {
                ToolExecutionResultMessage tool = (ToolExecutionResultMessage) message;
                record.put("role", "tool");
                record.put("id", tool.id());
                record.put("name", tool.toolName());
                record.put("content", tool.text());
                record.put("isError", Boolean.TRUE.equals(tool.isError()));
            } else {
                continue;
            }
            records.add(record);
        }
        return JSON.toJSONString(records);
    }

    @SuppressWarnings("unchecked")
    private List<dev.langchain4j.data.message.ChatMessage> deserializeCopilotTranscript(Object rawContent) {
        if (rawContent == null) {
            return List.of();
        }
        try {
            JSONArray records = JSON.parseArray(String.valueOf(rawContent));
            if (records == null) {
                return List.of();
            }
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            for (Object value : records) {
                if (!(value instanceof JSONObject)) {
                    continue;
                }
                JSONObject record = (JSONObject) value;
                String role = record.getString("role");
                if ("user".equals(role)) {
                    messages.add(UserMessage.from(StringUtils.defaultString(record.getString("content"))));
                } else if ("assistant".equals(role)) {
                    AiMessage.Builder builder = AiMessage.builder()
                            .text(StringUtils.defaultString(record.getString("content")));
                    String thinking = record.getString("thinking");
                    if (StringUtils.isNotBlank(thinking)) {
                        builder.thinking(thinking);
                    }
                    List<ToolExecutionRequest> calls = new ArrayList<>();
                    JSONArray rawCalls = record.getJSONArray("toolRequests");
                    if (rawCalls != null) {
                        for (Object rawCall : rawCalls) {
                            if (!(rawCall instanceof JSONObject)) continue;
                            JSONObject call = (JSONObject) rawCall;
                            calls.add(ToolExecutionRequest.builder()
                                    .id(call.getString("id"))
                                    .name(call.getString("name"))
                                    .arguments(StringUtils.defaultIfBlank(call.getString("arguments"), "{}"))
                                    .build());
                        }
                    }
                    if (!calls.isEmpty()) {
                        builder.toolExecutionRequests(calls);
                    }
                    messages.add(builder.build());
                } else if ("tool".equals(role)) {
                    messages.add(ToolExecutionResultMessage.builder()
                            .id(record.getString("id"))
                            .toolName(record.getString("name"))
                            .text(StringUtils.defaultString(record.getString("content")))
                            .isError(record.getBooleanValue("isError"))
                            .build());
                }
            }
            return messages;
        } catch (Exception error) {
            log.warn("failed to restore Copilot transcript snapshot", error);
            return List.of();
        }
    }

    private void compactMemory() {
        this.memory.compact();
    }
}
