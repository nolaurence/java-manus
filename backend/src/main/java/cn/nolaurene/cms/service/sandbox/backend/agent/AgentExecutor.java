package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.dto.ConversationResponse;
import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMemory;
import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.*;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.utils.ReActParser;
import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.SseMessageForwardService;
import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.entity.AgentSessionServerDO;
import cn.nolaurene.cms.service.AgentSessionServerService;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.model.chat.ChatModel;
import io.mybatis.mapper.example.Example;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    public void initialize(ToolRegistry tools, ChatModel chatModel, Agent agent) {
        this.tools = tools;
        this.chatModel = chatModel;
        this.MAX_ROUNDS = agent.getMaxLoop();
        this.agent = agent;
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
                        syncRespondThought(START_SIGNAL, emitter);
                        syncRespondThought(thought, emitter);
                        syncRespondThought(DONE_SIGNAL, emitter);
                        syncRespondContent(rawPlan, emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        addMessageToMemory(new ChatMessage(ChatMessage.Role.assistant, SSEEventType.MESSAGE, "**Thinking:**\n" + thought + "\n\n**Response:**\n" + rawPlan));
                        plan = ReActParser.parsePlan(rawPlan);
                        if (null == plan) {
                            log.error("[PLAN ACT] Failed to parse plan for round {}, skipping to next round.", round);
                            continue;
                        }

                        plan.getSteps().forEach(step -> step.setStatus(StepEventStatus.pending.getCode()));
                        syncRespondPlan(plan, emitter);
                        addMessageToMemory(new ChatMessage(ChatMessage.Role.assistant, SSEEventType.PLAN, JSON.toJSONString(plan)));

                        agentStatus = AgentStatus.EXECUTING;
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
                        break;

                    case CONCLUDING:
                        String conclusion = agent.getExecutor().conclude(chatModel, memory.getHistory());

                        syncRespondThought(START_SIGNAL, emitter);
                        syncRespondThought(DONE_SIGNAL, emitter);
                        syncRespondContent(conclusion, emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        saveAssistantMessage(conclusion, SSEEventType.MESSAGE);
                        agentStatus = AgentStatus.IDLE;
                        round = MAX_ROUNDS + 1;
                        break;

                    case COMPLETED:
                    default:
                        break;

                }
            } catch (Exception e) {
                log.error("[PLAN ACT] Error when creating plan for round: {}, error: ", round, e);
                break;
            }
        }
    }

    private void syncRespondThought(String reasoningContent, SseEmitter sseEmitter) {
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setReasoningContentDelta(reasoningContent);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && sseEmitter != null) {
            sendOrForwardMessage(sseEmitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEvent));
        } else {
            logSseEvent(SSEEventType.MESSAGE.getType(), messageEvent);
        }
    }

    private void syncRespondContent(String content, SseEmitter sseEmitter) {
        MessageEventData messageEvent = new MessageEventData();
        messageEvent.setContentDelta(content);
        messageEvent.setTimestamp(System.currentTimeMillis());
        if (frontendConnected.get() && sseEmitter != null) {
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

    private void reportStep(StepEventStatus status, String description, SseEmitter sseEmitterOpt) {
        addMessageToMemory(new ChatMessage(ChatMessage.Role.assistant, SSEEventType.STEP, description));
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

    private void setupSseEmitterListeners(SseEmitter sseEmitter) {
        sseEmitter.onCompletion(() -> {
            log.info("SSE connection completed/onCompletion (user likely left)");
            if (frontendConnected.compareAndSet(true, false)) {
                log.info("Frontend connection marked as disconnected via onCompletion.");
            }
        });
        sseEmitter.onError((Throwable t) -> {
            log.warn("SSE connection encountered error/onError", t);
            if (frontendConnected.compareAndSet(true, false)) {
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
