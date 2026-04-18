package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.dto.skill.SkillDefinitionDTO;
import cn.nolaurene.cms.common.dto.skill.SkillExecutionRequest;
import cn.nolaurene.cms.common.dto.skill.SkillExecutionResult;
import cn.nolaurene.cms.common.dto.skill.SkillRoutingDecision;
import cn.nolaurene.cms.common.dto.skill.ToolDefinition;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.MessageEventData;
import cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillExecutionEngine;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillFileStorageService;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillRoutingService;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillToolProvider;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.loadPrompt;
import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.render;

/**
 * Execution sub-agent for Plan steps.
 * Uses langchain4j native function calling for tool invocation.
 */
@Slf4j
@Service
public class ExecutionSubAgent {

    private static final int MCP_TOOL_RETRY_TIMES = 3;
    private static final String THINK_TOOL_NAME = "dummy-server-think";

    @Resource
    private ConversationHistoryService conversationHistoryService;

    @Autowired
    private SkillToolProvider skillToolProvider;

    @Autowired
    private SkillExecutionEngine skillExecutionEngine;

    @Autowired
    private SkillRoutingService skillRoutingService;

    @Autowired
    private SkillFileStorageService skillFileStorageService;

    /**
     * Execute a single step with a tool-calling loop.
     * Uses langchain4j's native function calling: the LLM returns structured ToolExecutionRequests,
     * which are executed via the McpToolProvider, and results are fed back.
     *
     * New: Added routing decision before execution to choose between DIRECT_TOOL and SKILL.
     */
    public String executeStepWithLoop(ChatModel chatModel,
                                      Executor executor,
                                      Plan plan,
                                      Step currentStep,
                                      List<Step> completedSteps,
                                      int maxRounds,
                                      SseEmitter emitterOpt,
                                      Agent agent) throws IOException {

        log.info("[ExecutionSubAgent] executeStepWithLoop start, goal: {}, currentStep: {}, maxRounds: {}",
                plan.getGoal(), currentStep.getDescription(), maxRounds);

        // Step 1: Routing Decision - Let LLM decide whether to use DIRECT_TOOL or SKILL
        SkillRoutingDecision routingDecision = skillRoutingService.route(
                chatModel, agent, plan, currentStep, completedSteps);

        log.info("[ExecutionSubAgent] Routing decision: type={}, reason={}",
                routingDecision.getDecisionType(), routingDecision.getReason());

        // Step 2: Execute based on routing decision
        String finalResult;
        if (routingDecision.isSkill()) {
            // Execute using Skill
            finalResult = executeStepWithSkill(chatModel, plan, currentStep, completedSteps,
                    maxRounds, emitterOpt, agent);
        } else {
            // Execute using Direct Tools (original for loop logic)
            finalResult = executeStepWithDirectTools(chatModel, plan, currentStep, completedSteps,
                    maxRounds, emitterOpt, agent);
        }

        log.info("[ExecutionSubAgent] executeStepWithLoop end, final result length: {}", finalResult.length());
        return finalResult;
    }

    /**
     * Execute step using Direct MCP Tools (browser, shell, file tools).
     * This is the original for loop logic preserved for simple tasks.
     */
    private String executeStepWithDirectTools(ChatModel chatModel,
                                               Plan plan,
                                               Step currentStep,
                                               List<Step> completedSteps,
                                               int maxRounds,
                                               SseEmitter emitterOpt,
                                               Agent agent) throws IOException {

        // Filter out skill tools, only use MCP tools (browser, shell, file)
        List<ToolSpecification> toolSpecs = buildToolSpecsWithThink(
                agent.getToolSpecifications().stream()
                        .filter(tool -> !tool.name().startsWith(SkillToolProvider.SKILL_TOOL_PREFIX))
                        .collect(Collectors.toList())
        );

        // Build initial messages
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = loadPrompt("prompts/system.jinja");
        String executorSystemPrompt = loadPrompt("prompts/executionSystemPrompt.jinja");
        messages.add(SystemMessage.from(systemPrompt + "\n" + executorSystemPrompt));

        // Add execution context
        String executionContext = buildExecutionContext(plan, currentStep, completedSteps);
        messages.add(UserMessage.from(executionContext));

        log.info("[ExecutionSubAgent] executeStepWithDirectTools start for step: {}", currentStep.getDescription());

        String finalResult = "";

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ExecutionSubAgent] DirectTool Round {}/{} for step: {}", round, maxRounds, currentStep.getDescription());

            // Call LLM with tool specifications
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();

            ChatResponse response;
            try {
                response = chatModel.chat(request);
            } catch (RateLimitException e) {
                log.error("[ExecutionSubAgent] Rate limit reached in round {}: {}", round, e.getMessage());
                throw e;
            }
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

                    // For shell tools, inject agentId as the default id parameter
                    ToolExecutionRequest finalToolRequest = toolRequest;
                    String finalArguments = arguments;
                    if (toolName.startsWith("shell_")) {
                        finalToolRequest = injectAgentIdForShellTool(toolRequest, agent.getAgentId());
                        finalArguments = finalToolRequest.arguments();
                    }

                    // Report tool event to frontend via SSE
                    reportToolEvent(toolName, finalArguments, agent, emitterOpt);

                    // Execute tool via MCP Client directly
                    String observation = executeToolWithRetry(toolName, finalToolRequest, agent);
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

        return finalResult;
    }

    /**
     * Execute step using a Skill with 5-step workflow:
     * 1. Load enabled skills' YAML for skill selection
     * 2. Load selected skill's full content (skill.md) for command decision
     * 3. Execute command via shell_exec in sandbox
     * 4. Check if task is completed
     * 5. Loop 1-4 until completion or max rounds reached
     */
    private String executeStepWithSkill(ChatModel chatModel,
                                         Plan plan,
                                         Step currentStep,
                                         List<Step> completedSteps,
                                         int maxRounds,
                                         SseEmitter emitterOpt,
                                         Agent agent) throws IOException {

        String sessionId = agent.getAgentId();

        // Step 1: Select skill from enabled skills based on current context
        String selectedSkillId = selectSkillForExecution(chatModel, plan, currentStep, completedSteps, agent);
        if (selectedSkillId == null || selectedSkillId.isEmpty()) {
            log.error("[ExecutionSubAgent] No skill selected for execution");
            return "Error: No suitable skill found for current step";
        }

        // Remove skill_ prefix if present
        if (selectedSkillId.startsWith(SkillToolProvider.SKILL_TOOL_PREFIX)) {
            selectedSkillId = selectedSkillId.substring(SkillToolProvider.SKILL_TOOL_PREFIX.length());
        }

        log.info("[ExecutionSubAgent] Selected skill for execution: {}", selectedSkillId);

        // Load full skill definition with documents
        SkillDefinitionDTO skill = loadFullSkillDefinition(selectedSkillId);
        if (skill == null) {
            log.error("[ExecutionSubAgent] Skill not found: {}", selectedSkillId);
            return "Error: Skill not found: " + selectedSkillId;
        }

        // Build messages for skill execution loop
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = loadPrompt("prompts/system.jinja");
        String executorSystemPrompt = loadPrompt("prompts/executionSystemPrompt.jinja");
        messages.add(SystemMessage.from(systemPrompt + "\n" + executorSystemPrompt));

        // Step 2-5: Execution loop with skill content
        String finalResult = "";
        int consecutiveTextOnlyRounds = 0;
        final int MAX_TEXT_ONLY_ROUNDS = 3;

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ExecutionSubAgent] Skill Round {}/{} for skill: {}", round, maxRounds, selectedSkillId);

            // Build context with skill content for command decision
            String executionContext = buildSkillExecutionContextWithContent(plan, currentStep, completedSteps, skill, round);
            List<ChatMessage> roundMessages = new ArrayList<>(messages);
            roundMessages.add(UserMessage.from(executionContext));

            // Call LLM to get command decision
            ChatRequest request = ChatRequest.builder()
                    .messages(roundMessages)
                    .build();

            log.info("[ExecutionSubAgent] Skill Round {} - LLM request messages (count={}): {}", round, roundMessages.size(), renderMessageToLog(roundMessages));

            ChatResponse response;
            try {
                response = chatModel.chat(request);
            } catch (RateLimitException e) {
                log.error("[ExecutionSubAgent] Rate limit reached in skill round {}: {}", round, e.getMessage());
                throw e;
            }
            AiMessage aiMessage = response.aiMessage();

            String aiText = aiMessage.text();
            log.info("[ExecutionSubAgent] Skill Round {} - LLM response: {}", round, aiText);
            if (aiText == null || aiText.isEmpty()) {
                log.warn("[ExecutionSubAgent] Skill Round {} - AI message has no text", round);
                break;
            }

            // Add AI message to main conversation
            messages.add(aiMessage);

            // Check if step is completed
            if (aiText.contains("COMPLETED:") || aiText.contains("Step completed")) {
                finalResult = aiText.replace("COMPLETED:", "").replace("Step completed", "").trim();
                log.info("[ExecutionSubAgent] Skill execution completed in round {}: {}", round, selectedSkillId);
                break;
            }

            // Step 3: Extract and execute shell command
            String command = extractCommand(aiText);
            if (command != null && !command.isEmpty()) {
                log.info("[ExecutionSubAgent] Executing skill command: {}", command);

                // Report tool event
                reportToolEvent("shell_skill_execute", "{\"command\": \"" + command + "\"}", agent, emitterOpt);

                // Execute command via shell service
                String observation = executeSkillCommand(selectedSkillId, sessionId, command, agent);
                log.info("[ExecutionSubAgent] Skill command result: {}", observation);

                // Step 4: Add result to messages for completion check
                messages.add(UserMessage.from("Command output:\n" + observation));

                // Check if step is completed after command execution
                String checkResult = checkStepCompletion(chatModel, messages, currentStep);
                if (checkResult != null) {
                    finalResult = checkResult;
                    log.info("[ExecutionSubAgent] Step completed after command execution in round {}", round);
                    break;
                }

                // Sleep to prevent too fast execution
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                // Reset consecutive text-only counter since we executed a command
                consecutiveTextOnlyRounds = 0;
                // Step 5: Continue to next round
                continue;
            }

            // LLM provided text response without command
            finalResult = aiText;
            consecutiveTextOnlyRounds++;
            log.info("[ExecutionSubAgent] Skill execution returned text in round {}: {} (consecutive text-only: {})",
                    round, selectedSkillId, consecutiveTextOnlyRounds);

            // Check if step is completed
            String checkResult = checkStepCompletion(chatModel, messages, currentStep);
            if (checkResult != null) {
                finalResult = checkResult;
                break;
            }

            // Break if LLM keeps returning text without commands for too many rounds
            if (consecutiveTextOnlyRounds >= MAX_TEXT_ONLY_ROUNDS) {
                log.warn("[ExecutionSubAgent] Breaking skill loop: {} consecutive text-only rounds without progress for skill: {}",
                        consecutiveTextOnlyRounds, selectedSkillId);
                break;
            }
        }

        if (finalResult.isEmpty()) {
            finalResult = "Skill execution completed for: " + selectedSkillId;
        }

        return finalResult;
    }

    /**
     * Step 1: Select skill from enabled skills based on current context.
     * Loads YAML-only summary of enabled skills for LLM to choose.
     */
    private String selectSkillForExecution(ChatModel chatModel,
                                           Plan plan,
                                           Step currentStep,
                                           List<Step> completedSteps,
                                           Agent agent) throws IOException {
        log.info("[ExecutionSubAgent] Step 1: Selecting skill from enabled skills");

        // Get enabled skills for the user
        List<SkillDefinitionDTO> enabledSkills = getEnabledSkillsForUser(agent);
        if (enabledSkills.isEmpty()) {
            log.warn("[ExecutionSubAgent] No enabled skills found for user");
            return null;
        }

        // Build skills YAML summary
        StringBuilder skillsYaml = new StringBuilder();
        for (SkillDefinitionDTO skill : enabledSkills) {
            skillsYaml.append("### Skill: ").append(skill.getSkillId()).append("\n");
            skillsYaml.append("```yaml\n");
            skillsYaml.append("name: ").append(skill.getName()).append("\n");
            skillsYaml.append("description: ").append(skill.getDescription()).append("\n");
            if (skill.getVersion() != null) {
                skillsYaml.append("version: ").append(skill.getVersion()).append("\n");
            }
            if (skill.getCompatibility() != null) {
                skillsYaml.append("compatibility: ").append(skill.getCompatibility()).append("\n");
            }
            skillsYaml.append("```\n\n");
        }

        // Load template and render
        String template = loadPrompt("prompts/selectSkillForExecution.jinja");
        Map<String, Object> context = new HashMap<>();
        context.put("goal", plan.getGoal());
        context.put("completedStepsSection", buildCompletedStepsSection(completedSteps, 100));
        context.put("currentStepDescription", currentStep.getDescription());
        context.put("skillsYaml", skillsYaml.toString());
        String prompt = render(template, context);

        // Call LLM for skill selection
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("You are a skill selector. Choose the most appropriate skill for the given task."));
        messages.add(UserMessage.from(prompt));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();

        try {
            ChatResponse response = chatModel.chat(request);
            String selectedSkillId = response.aiMessage().text().trim();

            // Clean up response
            if (selectedSkillId.startsWith("```") && selectedSkillId.endsWith("```")) {
                selectedSkillId = selectedSkillId.replace("```", "").trim();
            }

            if ("NONE".equalsIgnoreCase(selectedSkillId)) {
                log.info("[ExecutionSubAgent] No skill selected by LLM");
                return null;
            }

            // Validate selected skill is in enabled list
            final String finalSkillId = selectedSkillId;
            boolean isValid = enabledSkills.stream()
                    .anyMatch(s -> s.getSkillId().equals(finalSkillId));

            if (!isValid) {
                log.warn("[ExecutionSubAgent] LLM selected invalid skill: {}", selectedSkillId);
                return null;
            }

            log.info("[ExecutionSubAgent] Skill selected: {}", selectedSkillId);
            return selectedSkillId;

        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Failed to select skill: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get enabled skills for the user from agent context.
     */
    private List<SkillDefinitionDTO> getEnabledSkillsForUser(Agent agent) {
        List<SkillDefinitionDTO> skills = new ArrayList<>();

        // Extract skill IDs from tool specifications
        List<String> skillIds = agent.getToolSpecifications().stream()
                .filter(tool -> tool.name().startsWith(SkillToolProvider.SKILL_TOOL_PREFIX))
                .map(tool -> tool.name().substring(SkillToolProvider.SKILL_TOOL_PREFIX.length()))
                .map(this::denormalizeSkillId)
                .collect(Collectors.toList());

        // Load full skill definitions
        for (String skillId : skillIds) {
            SkillDefinitionDTO skill = skillToolProvider.getSkillDefinition(skillId);
            if (skill != null) {
                skills.add(skill);
            }
        }

        return skills;
    }

    /**
     * Denormalize skill ID from tool name format.
     */
    private String denormalizeSkillId(String normalizedId) {
        // The normalized ID uses underscores, we need to find the original
        // This is a simplified version - in practice, you might need to query the database
        return normalizedId.replace("_", "/");
    }

    /**
     * Load full skill definition including documents (skill.md, etc.).
     */
    private SkillDefinitionDTO loadFullSkillDefinition(String skillId) {
        SkillDefinitionDTO skill = skillToolProvider.getSkillDefinition(skillId);
        if (skill == null) {
            return null;
        }

        // Load documents from database
        // Note: This requires SkillDocumentMapper, which should be injected
        // For now, we use the existing skill definition
        return skill;
    }

    /**
     * Build skill execution context with full skill content.
     */
    private String buildSkillExecutionContextWithContent(Plan plan, Step currentStep,
                                                         List<Step> completedSteps,
                                                         SkillDefinitionDTO skill,
                                                         int currentRound) throws IOException {
        // Build skill documentation section from filesystem
        String skillDocSection = "";
        String skillContext = skillFileStorageService.getSkillContext(skill.getSkillId());
        if (StringUtils.isNotBlank(skillContext)) {
            skillDocSection = "## Skill Files (from /app/skills/extracted/" + skill.getSkillId() + ")\n" + skillContext;
        } else if (skill.getDocuments() != null) {
            skillDocSection = "## Skill Documentation\n(Skill documentation loaded)\n\n";
        }

        String template = loadPrompt("prompts/buildSkillExecutionContext.jinja");
        Map<String, Object> context = new HashMap<>();
        context.put("goal", plan.getGoal());
        context.put("skillId", skill.getSkillId());
        context.put("skillName", skill.getName());
        context.put("skillDescription", skill.getDescription());
        context.put("skillDocumentationSection", skillDocSection);
        context.put("completedStepsSection", buildCompletedStepsSection(completedSteps, 200));
        context.put("currentStepDescription", currentStep.getDescription());
        context.put("currentRound", String.valueOf(currentRound));
        return render(template, context);
    }

    /**
     * Execute a skill command via SkillExecutionEngine (which uses shell service).
     */
    private String executeSkillCommand(String skillId, String sessionId, String command, Agent agent) {
        try {
            // Build skill execution request
            SkillExecutionRequest skillRequest = new SkillExecutionRequest();
            skillRequest.setSkillId(skillId);
            skillRequest.setSessionId(sessionId);
            skillRequest.setWorkingDir("");

            // Set command as parameter
            Map<String, Object> params = new HashMap<>();
            params.put("command", command);
            skillRequest.setParams(params);

            // Execute skill via SkillExecutionEngine
            SkillExecutionResult result = skillExecutionEngine.execute(skillRequest, agent.getNativeMcpClient());

            if ("SUCCESS".equals(result.getStatus())) {
                return result.getOutput() != null ? result.getOutput() : "(command executed successfully)";
            } else {
                return "Command execution failed: " + (result.getError() != null ? result.getError() : "unknown error");
            }

        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Failed to execute skill command: {}", command, e);
            return "Command execution error: " + e.getMessage();
        }
    }

    /**
     * Extract command from AI text response.
     */
    private String extractCommand(String text) {
        // Try to extract from code blocks
        if (text.contains("```bash")) {
            int start = text.indexOf("```bash") + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }
        if (text.contains("```shell")) {
            int start = text.indexOf("```shell") + 8;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // Try to extract from "Command:" prefix
        if (text.contains("Command:")) {
            int start = text.indexOf("Command:") + 8;
            int end = text.indexOf("\n", start);
            if (end == -1) end = text.length();
            return text.substring(start, end).trim();
        }

        return null;
    }

    /**
     * Build the "Previously Completed Steps" section for prompt templates.
     * @param completedSteps list of completed steps
     * @param maxResultLength max length for each step's result before truncation
     * @return formatted section string, empty string if no completed steps
     */
    private String buildCompletedStepsSection(List<Step> completedSteps, int maxResultLength) {
        if (completedSteps == null || completedSteps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Previously Completed Steps\n");
        for (Step s : completedSteps) {
            sb.append("- ").append(s.getDescription());
            if (s.getStatus() != null) {
                sb.append(": ").append(s.getStatus());
            }
            if (s.getResult() != null) {
                String truncated = s.getResult().length() > maxResultLength
                        ? s.getResult().substring(0, maxResultLength) + "... (truncated)"
                        : s.getResult();
                sb.append(" | Result: ").append(truncated);
            }
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Build execution context message from plan and step info.
     */
    private String buildExecutionContext(Plan plan, Step currentStep, List<Step> completedSteps) throws IOException {
        String template = loadPrompt("prompts/buildExecutionContext.jinja");
        Map<String, Object> context = new HashMap<>();
        context.put("goal", plan.getGoal());
        context.put("completedStepsSection", buildCompletedStepsSection(completedSteps, 200));
        context.put("currentStepDescription", currentStep.getDescription());
        return render(template, context);
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
        String checkPrompt;
        try {
            String template = loadPrompt("prompts/checkStepCompletion.jinja");
            Map<String, Object> context = new HashMap<>();
            context.put("currentStepDescription", currentStep.getDescription());
            checkPrompt = render(template, context);
        } catch (IOException e) {
            log.warn("[ExecutionSubAgent] Failed to load checkStepCompletion template, using fallback", e);
            checkPrompt = String.format(
                    "Based on the tool execution results above, evaluate whether the current step has been completed.\n\n" +
                    "Current Step: %s\n\n" +
                    "Instructions:\n" +
                    "- If the step is COMPLETED: Respond with a brief summary starting with 'COMPLETED: ' followed by what was accomplished.\n" +
                    "- If the step is NOT COMPLETED: Respond with 'NOT_COMPLETED' and continue working on it using the available tools.\n\n" +
                    "Your response:",
                    currentStep.getDescription()
            );
        }

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
     * Also handles Skill tools via SkillExecutionEngine.
     */
    private String executeToolWithRetry(String toolName, ToolExecutionRequest request, Agent agent) {
        // Check if this is a Skill tool
        if (skillToolProvider.isSkillTool(toolName)) {
            return executeSkillTool(toolName, request, agent);
        }

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
     * Execute a Skill tool via SkillExecutionEngine.
     * Skill tools are executed in the sandbox shell environment.
     */
    private String executeSkillTool(String toolName, ToolExecutionRequest request, Agent agent) {
        String skillId = skillToolProvider.parseSkillIdFromToolName(toolName);
        if (skillId == null) {
            String errorMsg = "Cannot find Skill ID for tool: " + toolName;
            log.error("[ExecutionSubAgent] {}", errorMsg);
            return errorMsg;
        }

        log.info("[ExecutionSubAgent] Executing Skill tool: {} -> {}", toolName, skillId);

        try {
            // Parse arguments
            JSONObject argsJson = JSON.parseObject(request.arguments());
            if (argsJson == null) {
                argsJson = new JSONObject();
            }

            // Extract session_id from arguments, fallback to agentId
            String sessionId = argsJson.getString("session_id");
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = agent.getAgentId();
            }

            // Build skill execution request
            SkillExecutionRequest skillRequest = new SkillExecutionRequest();
            skillRequest.setSkillId(skillId);
            skillRequest.setSessionId(sessionId);
            skillRequest.setWorkingDir("");

            // Convert remaining arguments to params map
            Map<String, Object> params = new HashMap<>();
            for (String key : argsJson.keySet()) {
                if (!"session_id".equals(key)) {
                    params.put(key, argsJson.get(key));
                }
            }
            skillRequest.setParams(params);

            SkillExecutionResult result = skillExecutionEngine.execute(skillRequest, agent.getNativeMcpClient());

            log.info("[ExecutionSubAgent] Skill {} execution completed with status: {}", skillId, result.getStatus());

            if ("SUCCESS".equals(result.getStatus())) {
                return result.getOutput() != null ? result.getOutput() : "(skill executed successfully)";
            } else {
                return "Skill execution failed: " + (result.getError() != null ? result.getError() : "unknown error");
            }

        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Failed to execute Skill tool: {}", toolName, e);
            return "Skill execution error: " + e.getMessage();
        }
    }

    /**
     * Inject agentId as the id parameter for shell tools.
     * If the original arguments already contain an id, it will be overwritten with agentId.
     */
    private ToolExecutionRequest injectAgentIdForShellTool(ToolExecutionRequest originalRequest, String agentId) {
        try {
            JSONObject argsJson = JSON.parseObject(originalRequest.arguments());
            if (argsJson == null) {
                argsJson = new JSONObject();
            }
            // Always set id to agentId for shell tools
            argsJson.put("id", agentId);
            
            return ToolExecutionRequest.builder()
                    .id(originalRequest.id())
                    .name(originalRequest.name())
                    .arguments(argsJson.toJSONString())
                    .build();
        } catch (Exception e) {
            log.warn("[ExecutionSubAgent] Failed to inject agentId for shell tool: {}", e.getMessage());
            return originalRequest;
        }
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

    private String renderMessageToLog(List<ChatMessage> chatMessageList) {
        List<String> renderedMessage = new ArrayList<>();
        for (ChatMessage message : chatMessageList) {
            if (message.getClass().equals(SystemMessage.class)) {
                String systemPrompt = ((SystemMessage) message).text().replaceAll("\n", "\\n");
                renderedMessage.add("[system prompt]: " + systemPrompt);
            } else if (message.getClass().equals(UserMessage.class)) {
                List<String> userMessageContents = new ArrayList<>();
                ((UserMessage) message).contents().stream().forEach(content -> {
                    if (content.getClass().equals(TextContent.class)) {
                        userMessageContents.add(((TextContent) content).text());
                    } else if (content.getClass().equals(ImageContent.class)) {
                        userMessageContents.add("[Image]");
                    } else if (content.getClass().equals(VideoContent.class)) {
                        userMessageContents.add("[Video]");
                    } else {
                        userMessageContents.add("[Other Content Type]");
                    }
                });
                renderedMessage.add("[USER]: " + String.join("\n", userMessageContents));
            } else if (message.getClass().equals(AiMessage.class)) {
                renderedMessage.add("[AI]: " + ((AiMessage) message).text());
            } else {
                renderedMessage.add(message.toString());
            }
        }

        return String.join("\n", renderedMessage);
    }
}
