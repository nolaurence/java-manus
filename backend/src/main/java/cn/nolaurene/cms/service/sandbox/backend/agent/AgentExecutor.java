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
import io.mybatis.mapper.example.Example;
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
import java.net.NetworkInterface;
import java.net.SocketException;
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
            SseEmitter activeEmitter = currentSseEmitter != null ? currentSseEmitter : emitter;
            if (activeEmitter != null) {
                activeEmitter.send(SseEmitter.event()
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

    public void planAct(String input, SseEmitter emitter) {
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
                        syncConversationInfo(plan.getTitle(), AgentStatus.PLANNING);

                        // 发送 title SSE 事件给前端
                        TitleEventData titleEvent = new TitleEventData();
                        titleEvent.setTitle(plan.getTitle());
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

    public void skillBasedAgentLoop(String input, SseEmitter emitter) {
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
        syncConversationInfo(buildConversationTitle(input), AgentStatus.EXECUTING);
        sendTitleEvent(buildConversationTitle(input), emitter);

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
            conversationHistoryService.updateToolResult(toolMessageId, observation);
        }
        return ToolExecutionResultMessage.from(toolRequest, observation);
    }

    private String executeMcpToolWithRetry(String toolName, ToolExecutionRequest request) {
        McpClient mcpClient = selectMcpClient(toolName);
        if (mcpClient == null) {
            return "No MCP client available for tool: " + toolName;
        }

        Exception lastException = null;
        for (int i = 0; i < MCP_TOOL_RETRY_TIMES; i++) {
            try {
                ToolExecutionResult result = mcpClient.executeTool(request);
                return result.resultText() != null ? result.resultText() : "(empty result)";
            } catch (Exception e) {
                lastException = e;
                log.warn("[SKILL LOOP] tool {} failed, attempt {}/{}: {}",
                        toolName, i + 1, MCP_TOOL_RETRY_TIMES, e.getMessage());
                if (i < MCP_TOOL_RETRY_TIMES - 1) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return "Tool execution interrupted: " + interruptedException.getMessage();
                    }
                }
            }
        }

        return "Tool call error after retries: " + (lastException != null ? lastException.getMessage() : "unknown");
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

    private void sendTitleEvent(String title, SseEmitter emitter) {
        TitleEventData titleEvent = new TitleEventData();
        titleEvent.setTitle(title);
        titleEvent.setTimestamp(System.currentTimeMillis());
        sendOrForwardMessage(emitter, SSEEventType.TITLE.getType(), titleEvent);
    }

    private String buildConversationTitle(String input) {
        String title = StringUtils.normalizeSpace(input);
        if (StringUtils.isBlank(title)) {
            return "New Chat";
        }
        return title.length() > 40 ? title.substring(0, 40) + "..." : title;
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
        if (conversationHistoryService == null) {
            return null;
        }
        try {
            memory.add(new ChatMessage(ChatMessage.Role.assistant, eventType, content));

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
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

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

    private void ensureMemory() {
        if (memory.isEmpty()) {
            Example<ConversationHistoryDO> example = new Example<>();
            List<ConversationResponse> sessionConversations = conversationHistoryService.getSessionConversations(agent.getAgentId());

            sessionConversations.forEach(conversation -> {
                switch(conversation.getMessageType()) {
                    case USER:
                        memory.add(new ChatMessage(ChatMessage.Role.user, conversation.getEventType(), JSON.toJSONString(conversation.getContent())));
                        break;
                    case ASSISTANT:
                        memory.add(new ChatMessage(ChatMessage.Role.assistant, conversation.getEventType(), JSON.toJSONString(conversation.getContent())));
                        break;
                    default:
                        break;
                }
            });
        }
    }

    private void addMessageToMemory(ChatMessage message) {
        switch(message.getRole()) {
            case user:
                saveUserMessage(message.getContent());
                break;
            case assistant:
                saveAssistantMessage(message.getContent(), message.getEventType());
                break;
        }
        this.memory.add(message);
    }

    private void compactMemory() {
        this.memory.compact();
    }
}
