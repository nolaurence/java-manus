package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMemory;
import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.ToolCall;
import cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.utils.ReActParser;
import cn.nolaurene.cms.utils.Fastjson2LenientToolParser;
import com.alibaba.fastjson2.JSON;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

/**
 * PLAN 执行阶段的子 Agent：
 * - 仅基于 Plan 中的信息构造上下文，不依赖全局对话 memory；
 * - 工具执行阶段由外层调用 executeTools，通过 MCP 实时获取 shell / browser 上下文。
 */
@Slf4j
@Component
public class ExecutionSubAgent {

    private static final int MCP_TOOL_RETRY_TIMES = 3;

    @Resource
    private ConversationHistoryService conversationHistoryService;

    @FunctionalInterface
    public interface ToolExecutor {
        String execute(int round, List<ToolCall> toolCallsFromAI, SseEmitter emitterOpt);
    }

    @FunctionalInterface
    public interface ToolReporter {
        void report(ToolCall toolCall, String toolType);
    }

    /**
     * 内部执行方法：基于独立上下文生成工具调用命令
     * 不依赖全局 Executor，使用专门为 ExecutionSubAgent 设计的提示词
     */
    private String executeStepInternal(LlmClient llmClient,
                                       ChatMemory memory,
                                       List<Step> completedSteps,
                                       String goal,
                                       String step,
                                       String tools) throws IOException {
        
        // 构建专门用于 ExecutionSubAgent 的提示词
        String executionPrompt = buildExecutionPrompt(completedSteps, goal, step, tools);
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.addAll(memory.getHistory());
        messages.add(new ChatMessage(ChatMessage.Role.user, executionPrompt));
        
        String llmResponse = llmClient.chat(messages);
        return ReActParser.parseOpenAIStyleResponse(llmResponse);
    }
    
    /**
     * 构建 ExecutionSubAgent 专用的执行提示词
     */
    private String buildExecutionPrompt(List<Step> completedSteps, String goal, String step, String tools) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are executing a specific step in a plan. Focus ONLY on the current step.\n\n");
        
        prompt.append("## Available Tools\n");
        prompt.append(tools).append("\n\n");
        
        prompt.append("## Tool Use Formatting\n");
        prompt.append("Use XML format for tool calls:\n");
        prompt.append("<tool_use>\n");
        prompt.append("<name>tool_name</name>\n");
        prompt.append("<arguments>{\"param\": \"value\"}</arguments>\n");
        prompt.append("</tool_use>\n\n");
        
        if (completedSteps != null && !completedSteps.isEmpty()) {
            prompt.append("## Previously Completed Steps\n");
            for (int i = 0; i < completedSteps.size(); i++) {
                Step s = completedSteps.get(i);
                if (s.getResult() != null && !s.getResult().isEmpty()) {
                    // 只显示最近完成步骤的简要结果
                    if (i == completedSteps.size() - 1) {
                        prompt.append("Step ").append(s.getDescription()).append(": ")
                              .append(truncateResult(s.getResult(), 200)).append("\n");
                    }
                }
            }
            prompt.append("\n");
        }
        
        prompt.append("## Current Goal\n");
        prompt.append(goal).append("\n\n");
        
        prompt.append("## Current Step to Execute\n");
        prompt.append(step).append("\n\n");
        
        prompt.append("## Instructions\n");
        prompt.append("1. Analyze the current step and determine what tools to use\n");
        prompt.append("2. Execute the necessary tools to accomplish this step\n");
        prompt.append("3. Return ONLY the tool calls needed for this specific step\n");
        prompt.append("4. Do not overthink or plan beyond the current step\n");
        
        return prompt.toString();
    }
    
    /**
     * 截断结果字符串，避免上下文过长
     */
    private String truncateResult(String result, int maxLength) {
        if (result == null || result.length() <= maxLength) {
            return result;
        }
        return result.substring(0, maxLength) + "... (truncated)";
    }

    /**
     * 执行单个 Step 的完整流程，包含多轮工具调用和完成判断。
     * 在最大 maxRounds 轮循环中：
     * 1. 根据 goal 和 steps 规划要做什么，使用工具获取上下文
     * 2. 执行工具（browser mcpclient 或 vanilla mcpclient）
     * 3. 使用大模型判断是否完成当前 step，未完成继续循环，完成则跳出
     *
     * @param llmClient      LLM 客户端
     * @param executor       执行器
     * @param plan           当前计划
     * @param currentStep    当前执行的步骤
     * @param completedSteps 已完成的步骤列表
     * @param xmlToolsInfo   XML 格式的工具信息
     * @param maxRounds      最大循环轮数（20 或 40）
     * @param emitterOpt     SSE 发射器（可选）
     * @param agent          Agent 对象，包含 MCP 客户端
     * @param toolReporter   工具报告回调
     * @return 执行结果
     * @throws IOException IO 异常
     */
    public String executeStepWithLoop(LlmClient llmClient,
                                      Executor executor,
                                      Plan plan,
                                      Step currentStep,
                                      List<Step> completedSteps,
                                      String xmlToolsInfo,
                                      int maxRounds,
                                      SseEmitter emitterOpt,
                                      Agent agent,
                                      ToolReporter toolReporter) throws IOException {
        ChatMemory execMemory = new ChatMemory();

        // 初始化上下文：将 Plan 的关键信息压缩成一条 user 消息
        Map<String, Object> planContext = new HashMap<>();
        planContext.put("goal", plan.getGoal());
        planContext.put("title", plan.getTitle());
        planContext.put("currentStep", currentStep.getDescription());
        planContext.put("steps", plan.getSteps());
        execMemory.add(new ChatMessage(ChatMessage.Role.user, JSON.toJSONString(planContext)));

        log.info("[ExecutionSubAgent] executeStepWithLoop start, goal: {}, currentStep: {}, maxRounds: {}",
                plan.getGoal(), currentStep.getDescription(), maxRounds);

        String finalResult = "";

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ExecutionSubAgent] Round {}/{} for step: {}", round, maxRounds, currentStep.getDescription());

            // 1. 规划：使用独立的执行提示词生成工具调用命令
            String executionCommand = executeStepInternal(
                    llmClient,
                    execMemory,
                    completedSteps,
                    plan.getGoal(),
                    currentStep.getDescription(),
                    xmlToolsInfo
            );

            // 2. 解析工具调用
            List<ToolCall> toolCallsFromAI = ReActParser.parseToolCallsFromContent(executionCommand);
            log.info("[ExecutionSubAgent] Round {} - Execute command: {}", round, executionCommand);

            if (CollectionUtils.isEmpty(toolCallsFromAI)) {
                log.warn("[ExecutionSubAgent] No tool calls found in round {}, breaking loop.", round);
                finalResult = executionCommand;
                break;
            }

            // 3. 执行工具
            String observation = executeTools(round, toolCallsFromAI, agent, emitterOpt);
            log.info("[ExecutionSubAgent] Round {} - Tool observation: {}", round, observation);

            // 4. 将 observation 添加到上下文
            execMemory.add(new ChatMessage(ChatMessage.Role.user,
                    "Tool execution result:\n" + observation));

            // 5. 使用大模型判断是否完成当前 step
            boolean isCompleted = checkStepCompletion(llmClient, execMemory, plan.getGoal(), currentStep.getDescription());

            if (isCompleted) {
                log.info("[ExecutionSubAgent] Step completed in round {}: {}", round, currentStep.getDescription());
                finalResult = observation;
                break;
            } else {
                // 未完成，生成简单描述并继续
                String summary = generateResultSummary(llmClient, execMemory, observation);
                log.info("[ExecutionSubAgent] Round {} - Step not completed, summary: {}", round, summary);
                execMemory.add(new ChatMessage(ChatMessage.Role.assistant, summary));
            }

            finalResult = observation;
        }

        log.info("[ExecutionSubAgent] executeStepWithLoop end, final result length: {}", finalResult.length());
        return finalResult;
    }

    /**
     * 执行工具调用（参考 AgentExecutor 中的 executeTools 方法）
     */
    private String executeTools(int round, List<ToolCall> toolCallsFromAI, Agent agent, SseEmitter emitterOpt) {
        // 获取工具名称和 input schema 的映射
        Map<String, McpSchema.JsonSchema> toolInputSchemaMap = agent.getMcpTools().stream()
                .collect(Collectors.toMap(McpSchema.Tool::getName, McpSchema.Tool::getInputSchema));

        if (CollectionUtils.isEmpty(toolCallsFromAI)) {
            return "(no tools available)";
        }

        List<String> observations = new ArrayList<>();

        for (ToolCall toolCall : toolCallsFromAI) {
            String toolName = toolCall.getFunction().getName();

            // 检查工具是否存在于 MCP 工具列表中
            if (!toolInputSchemaMap.containsKey(toolName)) {
                String errorMsg = "[Tool Execution] Unknown tool: " + toolName;
                log.warn(errorMsg);
                observations.add(errorMsg);
                continue;
            }

            // 解析工具参数
            Map<String, Object> toolInput;
            try {
                String arguments = toolCall.getFunction().getArguments();
                if (arguments == null || arguments.trim().isEmpty()) {
                    String errorMsg = "Tool " + toolName + " has empty or null arguments";
                    log.error(errorMsg);
                    observations.add(errorMsg);
                    continue;
                }
                toolInput = Fastjson2LenientToolParser.parseArguments(arguments, toolInputSchemaMap.get(toolName));
            } catch (Exception e) {
                log.error("Failed to parse tool arguments for tool {}: {}", toolName, toolCall.getFunction().getArguments(), e);
                String errorMsg = "Failed to parse arguments for tool: " + toolName + ". Error: " + e.getMessage();
                observations.add(errorMsg);
                continue;
            }

            log.info("[ExecutionSubAgent] round: {}, tool call: {}, input: {}", round, toolName, JSON.toJSONString(toolInput));

            // 根据工具名称选择对应的 MCP 客户端和工具类型
            McpSyncClient mcpClient = null;
            String toolType = null;
            if (toolName.startsWith("browser")) {
                mcpClient = agent.getBrowserMcpClient();
                toolType = "browser";
            } else if (toolName.startsWith("shell")) {
                mcpClient = agent.getNativeMcpClient();
                toolType = "shell";
            } else if (toolName.startsWith("file")) {
                mcpClient = agent.getNativeMcpClient();
                toolType = "file";
            } else {
                String errorMsg = "Unknown tool type: " + toolName;
                log.warn(errorMsg);
                observations.add(errorMsg);
                continue;
            }

            // 调用工具报告回调
            // if (toolReporter != null) {
            //     toolReporter.report(toolCall, toolType);
            // }

            ToolEventData toolEventData = new ToolEventData();
            toolEventData.setTimestamp(System.currentTimeMillis());
            toolEventData.setName(toolType);
            toolEventData.setFunction(toolCall.getFunction().getName());
            toolEventData.setArgs(toolInput);
            reportTool(toolEventData, agent.getAgentId(), agent.getUserId(), emitterOpt);

            // 调用 MCP 工具（带重试）
            McpSchema.CallToolResult callToolResult = null;
            Exception lastException = null;
            for (int i = 0; i < MCP_TOOL_RETRY_TIMES; i++) {
                try {
                    callToolResult = mcpClient.callTool(new McpSchema.CallToolRequest(toolName, toolInput));
                    lastException = null;
                    break;
                } catch (Exception e) {
                    log.warn("[ExecutionSubAgent] 调用 MCP 工具【{}】失败，第 {} 次重试", toolName, i + 1, e);
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

            // 处理结果
            String observation;
            if (lastException != null) {
                observation = "Tool call error after retries: " + lastException.getMessage();
            } else if (callToolResult == null || (callToolResult.getIsError() != null && callToolResult.getIsError())) {
                observation = "Tool call error: " + JSON.toJSONString(callToolResult != null ? callToolResult.getContent() : "Unknown error");
            } else {
                observation = JSON.toJSONString(callToolResult.getContent());
            }

            observations.add(observation);
        }

        return String.join("\n", observations);
    }

    /**
     * 使用大模型检查当前 step 是否已完成
     */
    private boolean checkStepCompletion(LlmClient llmClient, ChatMemory execMemory, String goal, String stepDescription) {
        try {
            List<ChatMessage> checkMessages = new ArrayList<>();
            checkMessages.addAll(execMemory.getHistory());

            String checkPrompt = String.format(
                    "Based on the above conversation and tool execution results, " +
                            "has the following step been completed successfully?\n\n" +
                            "Goal: %s\n" +
                            "Step: %s\n\n" +
                            "Respond with ONLY 'true' if completed, or 'false' if not completed.",
                    goal, stepDescription
            );
            checkMessages.add(new ChatMessage(ChatMessage.Role.user, checkPrompt));

            String response = llmClient.chat(checkMessages);
            log.info("[ExecutionSubAgent] Step completion check response: {}", response);

            // 解析响应，判断是否完成
            String normalizedResponse = response.trim().toLowerCase();
            return normalizedResponse.contains("true") ||
                    normalizedResponse.contains("completed") ||
                    normalizedResponse.contains("done") ||
                    normalizedResponse.contains("success");

        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Error checking step completion", e);
            return false;
        }
    }

    /**
     * 生成执行结果的简单描述
     */
    private String generateResultSummary(LlmClient llmClient, ChatMemory execMemory, String observation) {
        try {
            List<ChatMessage> summaryMessages = new ArrayList<>();
            summaryMessages.addAll(execMemory.getHistory());

            String summaryPrompt = "Based on the tool execution result above, provide a brief one-sentence summary of what was accomplished or discovered. Be concise.";
            summaryMessages.add(new ChatMessage(ChatMessage.Role.user, summaryPrompt));

            String summary = llmClient.chat(summaryMessages);
            log.info("[ExecutionSubAgent] Generated summary: {}", summary);
            return summary;

        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Error generating summary", e);
            return "Tool execution completed.";
        }
    }

    private void reportTool(ToolEventData toolEventData, String sessionId, String userId, SseEmitter emitter) {
        try {
            if (emitter != null) {
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.TOOL.getType())
                        .data(toolEventData)
                        .id(String.valueOf(System.currentTimeMillis())));
            }
        } catch (Exception e) {
            log.error("直接发送SSE消息失败: agentId={}, eventName={}", sessionId, SSEEventType.TOOL.getType(), e);
        }

        conversationHistoryService.saveAssistantMessageWithId(JSON.toJSONString(toolEventData), SSEEventType.TOOL, userId, sessionId);
    }
}

