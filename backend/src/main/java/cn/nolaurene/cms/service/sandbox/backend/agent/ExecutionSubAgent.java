package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.MessageEventData;
import cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private static final String THINK_TOOL_NAME = "dummy-server-think";

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

        List<ToolSpecification> toolSpecs = buildToolSpecsWithThink(agent.getToolSpecifications());

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

                    // Handle dummy-server-think tool locally: send message to frontend and continue
                    if (THINK_TOOL_NAME.equals(toolName)) {
                        String thought = extractThought(arguments);
                        log.info("[ExecutionSubAgent] Round {} - think tool invoked, thought length: {}", round, thought.length());
                        sendMessageEvent(thought, agent, emitterOpt);
                        messages.add(ToolExecutionResultMessage.from(toolRequest, "Thought logged."));
                        continue;
                    }

                    // Report tool event to frontend via SSE
                    reportToolEvent(toolName, arguments, agent, emitterOpt);

                    // Execute tool via MCP Client directly
                    String observation = executeToolWithRetry(toolName, toolRequest, agent);
                    log.info("[ExecutionSubAgent] Round {} - Tool {} result: {}", round, toolName, observation);

                    // Add tool result to messages
                    messages.add(ToolExecutionResultMessage.from(toolRequest, observation));
                }

                // Sleep 1s to prevent execution from running too fast
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                // After tool execution, check if current step is completed
                String checkResult = checkStepCompletion(chatModel, messages, currentStep);
                if (checkResult != null) {
                    // Step is completed, return the result
                    finalResult = checkResult;
                    log.info("[ExecutionSubAgent] Step completed after tool execution in round {}: {}", 
                            round, currentStep.getDescription());
                    break;
                }

                // Step not completed, continue loop
                log.info("[ExecutionSubAgent] Step not yet completed, continuing to round {}", round + 1);
                continue;
            }

            // LLM returned text without tool calls - task is completed, break out of loop
            if (aiMessage.text() != null) {
                finalResult = aiMessage.text();
                log.info("[ExecutionSubAgent] Task completed in round {}: {}", round, currentStep.getDescription());
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
     * Build tool specifications list including the local dummy-server-think tool.
     */
    private List<ToolSpecification> buildToolSpecsWithThink(List<ToolSpecification> mcpToolSpecs) {
        List<ToolSpecification> specs = new ArrayList<>(mcpToolSpecs);
        ToolSpecification thinkSpec = ToolSpecification.builder()
                .name(THINK_TOOL_NAME)
                .description("Use the tool to think about something. It will not obtain new information or make any changes to the repository, but just log the thought. Use it when complex reasoning or brainstorming is needed.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("thought", "Your thoughts.")
                        .required("thought")
                        .build())
                .build();
        specs.add(thinkSpec);
        return specs;
    }

    /**
     * Check if the current step is completed by asking LLM.
     * @return completion summary if step is done, null if step needs more work
     */
    private String checkStepCompletion(ChatModel chatModel, List<ChatMessage> messages, Step currentStep) {
        String checkPrompt = String.format(
                "Based on the tool execution results above, evaluate whether the current step has been completed.\n\n" +
                "Current Step: %s\n\n" +
                "Instructions:\n" +
                "- If the step is COMPLETED: Respond with a brief summary starting with 'COMPLETED: ' followed by what was accomplished.\n" +
                "- If the step is NOT COMPLETED: Respond with 'NOT_COMPLETED' and continue working on it using the available tools.\n\n" +
                "Your response:",
                currentStep.getDescription()
        );

        List<ChatMessage> checkMessages = new ArrayList<>(messages);
        checkMessages.add(UserMessage.from(checkPrompt));

        ChatRequest checkRequest = ChatRequest.builder()
                .messages(checkMessages)
                .build();

        try {
            ChatResponse checkResponse = chatModel.chat(checkRequest);
            AiMessage checkAiMessage = checkResponse.aiMessage();
            String responseText = checkAiMessage.text();

            if (responseText != null && responseText.startsWith("COMPLETED:")) {
                // Add the check prompt and response to main messages for context continuity
                messages.add(UserMessage.from(checkPrompt));
                messages.add(checkAiMessage);
                return responseText.substring("COMPLETED:".length()).trim();
            }

            // Not completed, add to messages if LLM wants to continue with tools
            if (checkAiMessage.hasToolExecutionRequests()) {
                messages.add(UserMessage.from(checkPrompt));
                messages.add(checkAiMessage);
            }

            return null;
        } catch (Exception e) {
            log.warn("[ExecutionSubAgent] Failed to check step completion: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract the "thought" field from the think tool arguments JSON.
     */
    private String extractThought(String arguments) {
        try {
            JSONObject obj = JSON.parseObject(arguments);
            String thought = obj.getString("thought");
            return thought != null ? thought : arguments;
        } catch (Exception e) {
            return arguments;
        }
    }

    /**
     * Send a MESSAGE SSE event to the frontend (used for think tool output).
     */
    private void sendMessageEvent(String content, Agent agent, SseEmitter emitter) {
        MessageEventData messageEventData = new MessageEventData();
        messageEventData.setTimestamp(System.currentTimeMillis());
        messageEventData.setContentDelta(content);

        try {
            if (emitter != null) {
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.MESSAGE.getType())
                        .data(JSON.toJSONString(messageEventData))
                        .id(String.valueOf(System.currentTimeMillis())));
            }
        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Failed to send think message SSE event: agentId={}", agent.getAgentId(), e);
        }

        conversationHistoryService.saveAssistantMessageWithId(
                content, SSEEventType.MESSAGE,
                agent.getUserId(), agent.getAgentId());
    }

    /**
     * Select the appropriate MCP client based on tool name.
     * - browser_xxx tools -> browserMcpClient
     * - shell_xxx/file_xxx tools -> nativeMcpClient
     */
    private McpClient selectMcpClient(String toolName, Agent agent) {
        if (toolName.startsWith("browser")) {
            return agent.getBrowserMcpClient();
        } else {
            // shell, file, and other tools use native MCP client
            return agent.getNativeMcpClient();
        }
    }

    /**
     * Execute a tool with retry logic using MCP Client directly.
     */
    private String executeToolWithRetry(String toolName, ToolExecutionRequest request, Agent agent) {
        McpClient mcpClient = selectMcpClient(toolName, agent);
        if (mcpClient == null) {
            String errorMsg = "No MCP client available for tool: " + toolName;
            log.error("[ExecutionSubAgent] {}", errorMsg);
            return errorMsg;
        }

        Exception lastException = null;
        for (int i = 0; i < MCP_TOOL_RETRY_TIMES; i++) {
            try {
                log.info("[ExecutionSubAgent] Executing tool [{}] via MCP Client, attempt {}/{}", 
                        toolName, i + 1, MCP_TOOL_RETRY_TIMES);
                ToolExecutionResult result = mcpClient.executeTool(request);
                String resultText = result.resultText();
                return resultText != null ? resultText : "(empty result)";
            } catch (Exception e) {
                log.warn("[ExecutionSubAgent] Tool [{}] failed, retry {}/{}: {}", 
                        toolName, i + 1, MCP_TOOL_RETRY_TIMES, e.getMessage());
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
