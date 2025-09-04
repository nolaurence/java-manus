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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.TypeReference;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
public class AgentExecutor {

    private final int MAX_ROUNDS;
    private static final long ROUND_INTERVAL = 1000L; // 每轮间隔时间，单位毫秒
    private static final boolean USE_STREAM = false;
    private final ToolRegistry tools;
    @Setter
    @Getter
    private String systemPrompt;
    private final LlmClient llm;
    private final Agent agent;
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

    public AgentExecutor(ToolRegistry tools, LlmClient llm, String systemPrompt, Agent agent) {
        this.tools = tools;
        this.llm = llm;
        this.MAX_ROUNDS = agent.getMaxLoop();
        this.agent = agent;
        memory.add(new ChatMessage(ChatMessage.Role.system, systemPrompt));
    }

    public void planAct(String input, SseEmitter emitter) {
        this.currentSseEmitter = emitter;
        this.frontendConnected.set(true);

        // 设置连接监听器
        setupSseEmitterListeners(emitter);
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
                        syncRespondThought(START_SIGNAL, emitter);
                        syncRespondThought(ReActParser.parseThinking(rawPlan), emitter);
                        syncRespondThought(DONE_SIGNAL, emitter);
                        syncRespondContent(rawPlan, emitter);
                        syncRespondContent(DONE_SIGNAL, emitter);
                        plan = ReActParser.parsePlan(rawPlan);
                        if (null == plan) {
                            log.error("[PLAN ACT] Failed to parse plan for round {}, skipping to next round.", round);
                            continue; // 跳过当前轮次
                        }

                        // 规划出的step全部置为pending
                        plan.getSteps().forEach(step -> step.setStatus(StepEventStatus.pending.getCode()));
                        syncRespondPlan(plan, emitter);

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

                        String executionCommand = agent.getExecutor().executeStep(llm, memory.getHistory(), plan.getGoal(), currentStep.getDescription(), agent.getXmlToolsInfo());
                        List<ToolCall> toolCallsFromAI = ReActParser.parseToolCallsFromContent(executionCommand);

                        log.info("[PLAN ACT] Execute command for round {}: {}", round, executionCommand);
                        String observation = executeTools(round, toolCallsFromAI, emitter);
                        currentStep.setResult(observation);
                        currentStep.setStatus(StepEventStatus.completed.getCode());
                        reportStep(StepEventStatus.completed, currentStep.getDescription(), emitter);
                        syncRespondPlan(plan, emitter);

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

    // --- 修改后的 streamReact 方法 ---
    public void react(String input, SseEmitter sseEmitter) throws InterruptedException {
        this.currentSseEmitter = sseEmitter; // 保存当前 emitter
        this.frontendConnected.set(true); // 开始时标记为连接

        // 设置连接监听器
        setupSseEmitterListeners(sseEmitter);

        memory.add(new ChatMessage(ChatMessage.Role.user, input));

        for (int round = 0; round < MAX_ROUNDS; round++) {
            // --- 关键检查点：每轮开始前检查连接状态 ---
            String content;
            if (!frontendConnected.get()) {
                log.info("Frontend disconnected before round {}, switching to background mode.", round);
                content = executeRoundBackground(input, round);
            } else if (USE_STREAM) {
                content = executeRoundStream(input, round, sseEmitter);
            } else {
                content = executeRoundNonStream(input, round, sseEmitter);
            }
            if (ReActParser.parseExecutionDone(content)) {
                log.info("Execution done at round {}", round);
                break; // 如果已经完成，跳出循环
            }


            // --- 关键检查点：工具执行后检查连接状态 ---
            // 注意：executeRoundStream/Background 内部的工具执行逻辑需要能够感知连接状态变化
            // 如果在工具执行中途断开，executeRoundStream 可能会因为 send 失败而捕获异常并设置 frontendConnected=false
            // executeRoundBackground 则始终在后台运行
            // 这里我们假设如果 executeRoundStream 因断开而退出，或者 executeRoundBackground 完成，
            // 我们继续下一轮，下一轮开始时的检查会决定是继续流式还是后台
            // 为了简化，如果在一轮中任何时刻断开，后续所有轮次都在后台执行
            if (!frontendConnected.get()) {
                log.info("Frontend disconnected during/before round {}, remaining rounds will run in background.", round);
                // 执行剩余轮次的后台逻辑
                for (int remainingRound = round + 1; remainingRound < MAX_ROUNDS; remainingRound++) {
                    content = executeRoundBackground(input, remainingRound);
                    if (ReActParser.parseExecutionDone(content)) {
                        log.info("Execution done in background mode at round {}", remainingRound);
                        break; // 如果已经完成，跳出剩余轮次
                    }
                }
                break; // 跳出主循环
            }
        }

        // --- 执行完成 ---
        log.info("Agent execution loop finished.");
        // 如果前端仍然连接，则完成 SSE
        if (frontendConnected.get() && sseEmitter != null) {
            try {
                sseEmitter.send(SseEmitter.event().name("TASK_FINISHED").data("Execution completed.")); // 可选：发送完成信号
                sseEmitter.complete();
                log.info("SSE connection completed normally.");
            } catch (IOException e) {
                log.error("Error completing SSE connection", e);
            }
        } else if (!frontendConnected.get()) {
            log.info("Execution finished in background mode.");
            // 如果需要通知外部 Session 管理器任务在后台完成，可以通过其他机制（例如回调、状态更新）
            // 这里只是记录日志
            // 可以尝试通过 currentSseEmitter 发送（虽然已断开，但有时可以触发 onCompletion）
            SseEmitter emitterAtCompletion = this.currentSseEmitter;
            if (emitterAtCompletion != null) {
                try {
                    emitterAtCompletion.send(SseEmitter.event().name("TASK_FINISHED_BG").data("Execution completed in background."));
                } catch (IOException e) {
                    log.debug("Could not send background finish signal to old emitter.");
                }
                // 不要 complete() 已断开的 emitter
            }
        }
        this.currentSseEmitter = null; // 清除引用
    }

    // --- 新增：供外部 Session 管理器在前端重新连接时调用 ---
    public void resume(SseEmitter sseEmitter) {
        log.info("Resuming AgentExecutor for potential stream.");
        this.currentSseEmitter = sseEmitter; // 更新当前 emitter
        this.frontendConnected.set(true); // 标记为已连接
        // 注意：此实现不推送历史事件，仅恢复连接状态。
        // 如果需要推送历史事件，需要额外的事件缓冲机制。
        setupSseEmitterListeners(sseEmitter); // 重新设置监听器
        // 不主动推送任何内容，等待下一次 async* 调用或任务完成检查
    }

    // --- 流式单轮执行 ---
    private String executeRoundStream(String input, int round, SseEmitter sseEmitter) throws InterruptedException {
        StreamResource resource = llm.streamChat(memory.getHistory(), agent.getTools());
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8), 512);
        String line;
        StringBuilder thought = new StringBuilder();
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCallsFromAI = new ArrayList<>();
        int toolCount = 0;

        try {
            MessageEventData startMessageEvent = new MessageEventData();
            startMessageEvent.setReasoningContentDelta(START_SIGNAL);
            startMessageEvent.setTimestamp(System.currentTimeMillis());
            asyncResponse(startMessageEvent, sseEmitter);

            log.info("start stream response for round: {}, input: {}", round, input);

            while ((line = reader.readLine()) != null) {
                // --- 关键检查点：读取流时检查连接状态 ---
                if (!frontendConnected.get()) {
                    log.info("Frontend disconnected during stream read for round {}, switching to background.", round);
                    // 尝试清理资源
                    try { resource.getInputStream().close(); } catch (IOException ignored) {}
                    try { resource.getResponse().close(); } catch (IOException ignored) {}
                    // 这里假设我们处理已有的 toolCalls
                    executeTools(round, toolCallsFromAI, sseEmitter); // 传入 sseEmitter，内部会检查状态
                    return ""; // 退出当前轮次的流式执行
                }

                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String contentDelta = (String) JSONPath.eval(data, "$.choices[0].delta.content");
                    String thoughtDelta = (String) JSONPath.eval(data, "$.choices[0].delta.reasoning_content");
                    List<ToolCall> toolCalls = JSON.parseArray(JSON.toJSONString(JSONPath.eval(data, "$.choices[0].delta.tool_calls")), ToolCall.class);

                    if (StringUtils.isNotEmpty(thoughtDelta)) {
                        thought.append(thoughtDelta);
                        MessageEventData messageEvent = new MessageEventData();
                        messageEvent.setReasoningContentDelta(thoughtDelta);
                        messageEvent.setTimestamp(System.currentTimeMillis());
                        asyncResponse(messageEvent, sseEmitter);
                        continue;
                    }
                    if (StringUtils.isNotEmpty(thought.toString())) {
                        MessageEventData messageEvent = new MessageEventData();
                        messageEvent.setReasoningContentDelta(DONE_SIGNAL);
                        messageEvent.setTimestamp(System.currentTimeMillis());
                        log.info("round: {}, thought: {}", round, thought);
                        thought.setLength(0);
                        asyncResponse(messageEvent, sseEmitter);
                    }
                    if (StringUtils.isNotEmpty(contentDelta)) {
                        content.append(contentDelta);
                        MessageEventData messageEvent = new MessageEventData();
                        messageEvent.setContentDelta(contentDelta);
                        messageEvent.setTimestamp(System.currentTimeMillis());
                        asyncResponse(messageEvent, sseEmitter);
                        continue;
                    }
                    if (CollectionUtils.isNotEmpty(toolCalls)) {
                        String name = toolCalls.get(0).getFunction().getName();
                        String arguments = toolCalls.get(0).getFunction().getArguments();
                        if (StringUtils.isNotEmpty(name)) {
                            ToolCall toolCallFromAI = new ToolCall();
                            toolCallFromAI.setFunction(new Function());
                            toolCallFromAI.getFunction().setArguments("");
                            toolCallFromAI.getFunction().setName(name);
                            toolCallsFromAI.add(toolCallFromAI);
                            toolCount++;
                        }
                        if (StringUtils.isNotEmpty(arguments)) {
                            toolCallsFromAI.get(toolCount - 1).getFunction().setArguments(toolCallsFromAI.get(toolCount - 1).getFunction().getArguments() + arguments);
                        }
                    }
                }
            }

            // 发送 content done
            if (StringUtils.isNotEmpty(content.toString()) && frontendConnected.get()) { // 再次检查
                MessageEventData messageEvent = new MessageEventData();
                messageEvent.setContentDelta(DONE_SIGNAL);
                messageEvent.setTimestamp(System.currentTimeMillis());
                log.info("round: {}, content: {}", round, content);
                asyncResponse(messageEvent, sseEmitter);
            }

            resource.getInputStream().close();
            resource.getResponse().close();

        } catch (IOException e) {
            log.error("Error during stream read or resource close for round {}", round, e);
            // 可能是连接断开导致的 IOException，更新状态
            if (frontendConnected.compareAndSet(true, false)) { // 原子性地设置，避免重复日志
                log.info("IOException detected, marking frontend as disconnected for round {}.", round);
            }
            // 尝试清理资源
            try { resource.getInputStream().close(); } catch (IOException ignored) {}
            try { resource.getResponse().close(); } catch (IOException ignored) {}
            // 处理已累积的 toolCalls
            executeTools(round, toolCallsFromAI, sseEmitter);
            return ""; // 退出当前轮次
        }

        log.info("start tool execution (streaming) for round: {}, tool calls: {}", round, toolCallsFromAI.size());
        executeTools(round, toolCallsFromAI, sseEmitter); // 传入 sseEmitter
        return content.toString();
    }

    private String executeRoundNonStream(String input, int round, SseEmitter sseEmitter) {
        try {
            log.info("Executing round {} in background mode using llm.chat", round);

            // 1. 调用非流式接口
            // 注意：需要将 ChatMemory 转换为 llm.chat 需要的格式
            List<ChatMessage> chatMessages = memory.getHistory();
            // 假设 agent.getTools() 返回的是 llm.chat 需要的工具定义格式 List<JSONObject>
            List<JSONObject> toolsForChat = agent.getTools(); // 确保类型匹配

            String llmResponse = llm.chat(chatMessages);
            log.info("LLM non-stream response for round {}: {}", round, llmResponse);

            // 2. 解析 LLM 响应 (需要根据 llm.chat 的实际返回格式调整)
            // 假设 llmResponse 是一个包含 content, reasoning_content 和 tool_calls 的 JSON 对象

            String content = (String) JSONPath.eval(llmResponse, "$.choices[0].message.content");
            String reasoningContent = (String) JSONPath.eval(llmResponse, "$.choices[0].message.reasoning_content");
            memory.add(new ChatMessage(ChatMessage.Role.assistant, "<think>\n" + reasoningContent + "\n</think>\n\n" + content));
            List<ToolCall> toolCallsFromAI = new ArrayList<>();

            // Start Signal
            syncRespondThought(START_SIGNAL, sseEmitter);

            // Reasoning Content
            if (StringUtils.isNotEmpty(reasoningContent)) {
                Thread.sleep(500L);
                syncRespondThought(reasoningContent, sseEmitter);
                Thread.sleep(500L);
                syncRespondThought(DONE_SIGNAL, sseEmitter);
                log.info("round (non stream): {}, thought: {}", round, reasoningContent);
            }

            // Content
            if (StringUtils.isNotEmpty(content)) {
                Thread.sleep(500L);
                syncRespondContent("\n**Response:**\n" + content, sseEmitter);
                Thread.sleep(500L);
                syncRespondContent(DONE_SIGNAL, sseEmitter);
                log.info("round (no stream): {}, content: {}", round, content);

                try {
                    toolCallsFromAI.addAll(ReActParser.parseToolCallsFromContent(content));
                } catch (Exception e) {
                    log.error("Failed to parse tool calls from content in round {}", round, e);
                }
            }

            // 4. 处理工具调用 (非流式)
            if (CollectionUtils.isNotEmpty(toolCallsFromAI)) {
                log.info("start tool execution (background) for round: {}, tool calls: {}", round, toolCallsFromAI != null ? toolCallsFromAI.size() : 0);
                Thread.sleep(500L);
                executeTools(round, toolCallsFromAI, sseEmitter);
            } else {
                log.info("No tool calls found for round {}, skipping tool execution.", round);
            }

            Thread.sleep(ROUND_INTERVAL);
            return content;
        } catch (Exception e) {
            log.error("Error calling llm.chat or processing response for round {}", round, e);
            handleError("LLM background call failed: " + e.getMessage(), round);
            return "";
        }
    }

    // --- 后台（非流式）单轮执行 ---
    private String executeRoundBackground(String input, int round) {
        try {
            log.info("Executing round {} in background mode using llm.chat", round);

            // 1. 调用非流式接口
            // 注意：需要将 ChatMemory 转换为 llm.chat 需要的格式
            List<ChatMessage> chatMessages = memory.getHistory();
            // 假设 agent.getTools() 返回的是 llm.chat 需要的工具定义格式 List<JSONObject>
            List<JSONObject> toolsForChat = agent.getTools(); // 确保类型匹配

            String llmResponse = llm.chat(chatMessages);
            log.debug("LLM non-stream response for round {}: {}", round, llmResponse);

            // 2. 解析 LLM 响应 (需要根据 llm.chat 的实际返回格式调整)
            // 假设 llmResponse 是一个包含 content, reasoning_content 和 tool_calls 的 JSON 对象
            // *** 这是最关键的部分，必须根据你实际的 llm.chat 返回结构调整 ***
            JSONObject responseJson;
            try {
                responseJson = JSON.parseObject(llmResponse);
            } catch (Exception parseEx) {
                log.error("Failed to parse llm.chat response JSON for round {}", round, parseEx);
                handleError("Failed to parse LLM background response", round);
                return ""; // 退出当前轮次
            }

            String content = responseJson.getString("content");
            String reasoningContent = responseJson.getString("reasoning_content");
            List<ToolCall> toolCallsFromAI = new ArrayList<>();
            try {
                // 假设 tool_calls 是一个 JSONArray
                toolCallsFromAI = JSON.parseArray(responseJson.getJSONArray("tool_calls").toJSONString(), ToolCall.class);
            } catch (Exception toolParseEx) {
                log.warn("Failed to parse tool_calls from llm.chat response for round {}, might be null/empty.", round, toolParseEx);
                // toolCallsFromAI 保持为空列表
            }

            // 3. 模拟 SSE 事件顺序 (仅记录日志，不推送)
            // Start Signal
            MessageEventData startEvent = new MessageEventData();
            startEvent.setReasoningContentDelta(START_SIGNAL);
            startEvent.setTimestamp(System.currentTimeMillis());
            logSseEvent(SSEEventType.MESSAGE.getType(), startEvent); // 记录而非发送

            // Reasoning Content
            if (StringUtils.isNotEmpty(reasoningContent)) {
                MessageEventData thoughtEvent = new MessageEventData();
                thoughtEvent.setReasoningContentDelta(reasoningContent);
                thoughtEvent.setTimestamp(System.currentTimeMillis());
                logSseEvent(SSEEventType.MESSAGE.getType(), thoughtEvent);

                MessageEventData thoughtDoneEvent = new MessageEventData();
                thoughtDoneEvent.setReasoningContentDelta(DONE_SIGNAL);
                thoughtDoneEvent.setTimestamp(System.currentTimeMillis());
                logSseEvent(SSEEventType.MESSAGE.getType(), thoughtDoneEvent);
                log.info("round (bg): {}, thought: {}", round, reasoningContent);
            }

            // Content
            if (StringUtils.isNotEmpty(content)) {
                MessageEventData contentEvent = new MessageEventData();
                contentEvent.setContentDelta(content);
                contentEvent.setTimestamp(System.currentTimeMillis());
                logSseEvent(SSEEventType.MESSAGE.getType(), contentEvent);

                MessageEventData contentDoneEvent = new MessageEventData();
                contentDoneEvent.setContentDelta(DONE_SIGNAL);
                contentDoneEvent.setTimestamp(System.currentTimeMillis());
                logSseEvent(SSEEventType.MESSAGE.getType(), contentDoneEvent);
                log.info("round (bg): {}, content: {}", round, content);
            }

            // 4. 处理工具调用 (后台模式)
            log.info("start tool execution (background) for round: {}, tool calls: {}", round, toolCallsFromAI != null ? toolCallsFromAI.size() : 0);
            executeTools(round, toolCallsFromAI, null); // 传递 null 表示后台模式
            return content;
        } catch (Exception e) {
            log.error("Error calling llm.chat or processing response for round {}", round, e);
            handleError("LLM background call failed: " + e.getMessage(), round);
            return "";
        }
    }

    // --- 工具执行逻辑 (区分前台/后台) ---
    private String executeTools(int round, List<ToolCall> toolCallsFromAI, SseEmitter sseEmitterOpt) {
        List<String> mcpToolNames = agent.getMcpTools().stream().map(McpSchema.Tool::getName).collect(Collectors.toList());
        List<String> toolNames = tools.getToolNames();

        if (CollectionUtils.isNotEmpty(toolCallsFromAI)) {
            long toolStartTime = System.currentTimeMillis();
            try {
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

                    reportStep(StepEventStatus.pending, "wait to execute tool: " + toolName, sseEmitterOpt);

                    String observation = "Observation not set";
                    AssistantMessageType messageType = AssistantMessageType.SHELL; // Default

                    if (mcpToolNames.contains(toolName)) {
                        messageType = AssistantMessageType.BROWSER;
                        reportStep(StepEventStatus.running, "execute mcp tool: " + toolName, sseEmitterOpt);
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
                                callToolResult = agent.getMcpClient().callTool(new McpSchema.CallToolRequest(toolName, toolInput));
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
                            reportStep(StepEventStatus.failed, observation, sseEmitterOpt);
                        } else if (null == callToolResult || (null != callToolResult.getIsError() && callToolResult.getIsError())) {
                            observation = "Tool call error: " + JSON.toJSONString(callToolResult != null ? callToolResult.getContent() : "Unknown error");
                            reportStep(StepEventStatus.failed, observation, sseEmitterOpt);
                        } else {
                            observation = JSON.toJSONString(callToolResult.getContent());
                            reportStep(StepEventStatus.completed, observation, sseEmitterOpt);
                        }

                    } else if (toolNames.contains(toolName)) {
                        Tool tool = tools.get(toolName);
                        reportStep(StepEventStatus.running, "execute tool: " + toolName, sseEmitterOpt);
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

                        try {
                            observation = tool != null ? tool.run(JSON.toJSONString(toolInput), null) : "Tool not found";
                        } catch (Exception e) {
                            log.error("Error executing tool: {}", toolName, e);
                            observation = "Tool execution error: " + e.getMessage();
                        }
                        reportStep(StepEventStatus.completed, observation, sseEmitterOpt);

                    } else {
                        String errorMsg = "Unknown tool: " + toolName;
                        log.warn(errorMsg);
                        observation = errorMsg;
                        reportStep(StepEventStatus.failed, errorMsg, sseEmitterOpt);
                    }
                    log.info("add observation to memory: {}", observation);
                    memory.add(new ChatMessage(ChatMessage.Role.assistant, "Observation: " + observation));
                    return observation;
                }
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
                return agent.getMcpClient().callTool(new McpSchema.CallToolRequest(toolName, arguments));
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

    public static void main(String[] args) {
        String rawJSON = "{\"id\":\"01984c5f468a537577fa3bf2bef96606\",\"object\":\"chat.completion.chunk\",\"created\":1753627969,\"model\":\"Qwen/Qwen3-8B\",\"choices\":[{\"index\":0,\"delta\":{\"content\":null,\"reasoning_content\":null,\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"01984c5f5f6a7e0c178bdc9407f41ea8\",\"type\":\"function\",\"function\":{\"name\":\"browser_navigate\",\"arguments\":\"\"}}]},\"finish_reason\":null}],\"system_fingerprint\":\"\",\"usage\":{\"prompt_tokens\":2727,\"completion_tokens\":315,\"total_tokens\":3042,\"completion_tokens_details\":{\"reasoning_tokens\":313}}}";
        Object eval = JSONPath.eval(rawJSON, "$.choices[0].delta.tool_calls");
        System.out.println(eval.getClass());
        List<ToolCall> toolCalls = JSON.parseArray(JSON.toJSONString(JSONPath.eval(rawJSON, "$.choices[0].delta.tool_calls")), ToolCall.class);
    }
}