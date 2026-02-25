package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.loadPrompt;

/**
 * Execution sub-agent for Plan steps.
 * Uses langchain4j native function calling for tool invocation.
 */
@Slf4j
@Component
public class ExecutionSubAgent {

    private static final int MCP_TOOL_RETRY_TIMES = 3;

    @Resource
    private ConversationHistoryService conversationHistoryService;

    /**
     * Execute a single step with a tool-calling loop.
     * Uses langchain4j's native function calling: the LLM returns structured ToolExecutionRequests,
     * which are executed via the McpToolProvider, and results are fed back.
     */
    public String executeStepWithLoop(ChatModel chatModel,
                                      Executor executor,
                                      Plan plan,
                                      Step currentStep,
                                      List<Step> completedSteps,
                                      int maxRounds,
                                      SseEmitter emitterOpt,
                                      Agent agent) throws IOException {

        List<ToolSpecification> toolSpecs = agent.getToolSpecifications();

        // Build initial messages
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = loadPrompt("prompts/system.jinja");
        String executorSystemPrompt = loadPrompt("prompts/executionSystemPrompt.jinja");
        messages.add(SystemMessage.from(systemPrompt + "\n" + executorSystemPrompt));

        // Add execution context
        String executionContext = buildExecutionContext(plan, currentStep, completedSteps);
        messages.add(UserMessage.from(executionContext));

        log.info("[ExecutionSubAgent] executeStepWithLoop start, goal: {}, currentStep: {}, maxRounds: {}",
                plan.getGoal(), currentStep.getDescription(), maxRounds);

        String finalResult = "";

        // Cache tool executors from McpToolProvider
        Map<String, ToolExecutor> toolExecutorMap = buildToolExecutorMap(agent);

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ExecutionSubAgent] Round {}/{} for step: {}", round, maxRounds, currentStep.getDescription());

            // Call LLM with tool specifications
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();

            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();

            // Add AI message to conversation
            messages.add(aiMessage);

            // Check if LLM wants to call tools
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                log.info("[ExecutionSubAgent] Round {} - {} tool calls requested", round, toolRequests.size());

                for (ToolExecutionRequest toolRequest : toolRequests) {
                    String toolName = toolRequest.name();
                    String arguments = toolRequest.arguments();

                    log.info("[ExecutionSubAgent] Round {}, tool call: {}, args: {}", round, toolName, arguments);

                    // Report tool event to frontend via SSE
                    reportToolEvent(toolName, arguments, agent, emitterOpt);

                    // Execute tool via McpToolProvider
                    String observation = executeToolWithRetry(toolName, toolRequest, toolExecutorMap);
                    log.info("[ExecutionSubAgent] Round {} - Tool {} result length: {}", round, toolName, observation.length());

                    // Add tool result to messages
                    messages.add(ToolExecutionResultMessage.from(toolRequest, observation));
                }

                // After tool execution, continue loop to let LLM process results
                continue;
            }

            // LLM returned text without tool calls - step is done
            if (aiMessage.text() != null) {
                finalResult = aiMessage.text();
                log.info("[ExecutionSubAgent] Step completed in round {}: {}", round, currentStep.getDescription());
                break;
            }

            log.warn("[ExecutionSubAgent] Round {} - AI message has neither text nor tool calls", round);
            break;
        }

        log.info("[ExecutionSubAgent] executeStepWithLoop end, final result length: {}", finalResult.length());
        return finalResult;
    }

    /**
     * Build execution context message from plan and step info.
     */
    private String buildExecutionContext(Plan plan, Step currentStep, List<Step> completedSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Current Goal\n");
        sb.append(plan.getGoal()).append("\n\n");

        if (completedSteps != null && !completedSteps.isEmpty()) {
            sb.append("## Previously Completed Steps\n");
            for (Step s : completedSteps) {
                sb.append("- ").append(s.getDescription()).append(": ").append(s.getStatus());
                if (s.getResult() != null) {
                    String truncated = s.getResult().length() > 200
                            ? s.getResult().substring(0, 200) + "... (truncated)"
                            : s.getResult();
                    sb.append(" | Result: ").append(truncated);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Current Step to Execute\n");
        sb.append(currentStep.getDescription()).append("\n\n");
        sb.append("Execute this step using the available tools. ");
        sb.append("When the step is fully completed, respond with a summary of what was accomplished.\n");

        return sb.toString();
    }

    /**
     * Build a name -> ToolExecutor map from McpToolProvider.
     */
    private Map<String, ToolExecutor> buildToolExecutorMap(Agent agent) {
        try {
            ToolProviderResult providerResult = agent.getToolProvider()
                    .provideTools(ToolProviderRequest.builder().build());

            Map<String, ToolExecutor> map = new HashMap<>();
            for (Map.Entry<ToolSpecification, ToolExecutor> entry : providerResult.tools().entrySet()) {
                map.put(entry.getKey().name(), entry.getValue());
            }
            return map;
        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Failed to build tool executor map", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Execute a tool with retry logic.
     */
    private String executeToolWithRetry(String toolName, ToolExecutionRequest request,
                                         Map<String, ToolExecutor> toolExecutorMap) {
        ToolExecutor toolExecutor = toolExecutorMap.get(toolName);
        if (toolExecutor == null) {
            String errorMsg = "Unknown tool: " + toolName;
            log.warn("[ExecutionSubAgent] {}", errorMsg);
            return errorMsg;
        }

        Exception lastException = null;
        for (int i = 0; i < MCP_TOOL_RETRY_TIMES; i++) {
            try {
                String result = toolExecutor.execute(request, null);
                return result != null ? result : "(empty result)";
            } catch (Exception e) {
                log.warn("[ExecutionSubAgent] Tool [{}] failed, retry {}/{}", toolName, i + 1, MCP_TOOL_RETRY_TIMES, e);
                lastException = e;
                if (i < MCP_TOOL_RETRY_TIMES - 1) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "Tool execution interrupted: " + ie.getMessage();
                    }
                }
            }
        }

        return "Tool call error after retries: " + (lastException != null ? lastException.getMessage() : "unknown");
    }

    /**
     * Report tool execution event to SSE and persistence.
     */
    private void reportToolEvent(String toolName, String arguments, Agent agent, SseEmitter emitter) {
        String toolType;
        if (toolName.startsWith("browser")) {
            toolType = "browser";
        } else if (toolName.startsWith("shell")) {
            toolType = "shell";
        } else if (toolName.startsWith("file")) {
            toolType = "file";
        } else {
            toolType = "tool";
        }

        ToolEventData toolEventData = new ToolEventData();
        toolEventData.setTimestamp(System.currentTimeMillis());
        toolEventData.setName(toolType);
        toolEventData.setFunction(toolName);
        try {
            toolEventData.setArgs(JSON.parseObject(arguments, Map.class));
        } catch (Exception e) {
            Map<String, Object> fallbackArgs = new HashMap<>();
            fallbackArgs.put("raw", arguments);
            toolEventData.setArgs(fallbackArgs);
        }

        // Send SSE event
        try {
            if (emitter != null) {
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.TOOL.getType())
                        .data(toolEventData)
                        .id(String.valueOf(System.currentTimeMillis())));
            }
        } catch (Exception e) {
            log.error("Failed to send SSE tool event: agentId={}", agent.getAgentId(), e);
        }

        // Persist
        conversationHistoryService.saveAssistantMessageWithId(
                JSON.toJSONString(toolEventData), SSEEventType.TOOL,
                agent.getUserId(), agent.getAgentId());
    }
}
