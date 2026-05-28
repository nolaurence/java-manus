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
import cn.nolaurene.cms.service.sandbox.backend.session.GlobalAgentSessionManager;
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
import dev.langchain4j.exception.InvalidRequestException;
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
    private static final int COMPACTED_CONTEXT_MAX_CHARS = 60_000;

    @Resource
    private ConversationHistoryService conversationHistoryService;

    @Resource
    private GlobalAgentSessionManager globalAgentSessionManager;

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

        // Step 0: Deep Thinking - Let the model reason deeply about the current step before execution
//        performDeepThinking(chatModel, plan, currentStep, completedSteps, emitterOpt, agent);

        // Routing Decision - Let LLM decide whether to use DIRECT_TOOL or SKILL
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
            } catch (InvalidRequestException e) {
                if (!isContextLengthExceeded(e)) {
                    throw e;
                }
                log.warn("[ExecutionSubAgent] Context length exceeded in direct round {}, compacting and retrying once", round);
                messages = compactExecutionMessages(messages);
                request = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(toolSpecs)
                        .build();
                response = chatModel.chat(request);
            }
            AiMessage aiMessage = response.aiMessage();
            sendThinkingMessageEvent(aiMessage, agent, emitterOpt);

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
                        sendReasoningEvent(thought, 0, agent, emitterOpt);
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
     * Execute step using a Skill with phased context injection:
     * - Round 1: Inject SKILL.md + support file list, let LLM decide next action.
     * - Subsequent rounds: If LLM requests a file (READ_FILE instruction), load and inject
     *   that single file into context, then continue. Otherwise execute the shell command.
     *
     * This avoids dumping all file content upfront; files are loaded on-demand.
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

        // Load basic skill definition
        SkillDefinitionDTO skill = loadFullSkillDefinition(selectedSkillId);
        if (skill == null) {
            log.error("[ExecutionSubAgent] Skill not found: {}", selectedSkillId);
            return "Error: Skill not found: " + selectedSkillId;
        }

        // Pre-load SKILL.md and support file list (lightweight metadata only)
        String skillMdContent = skillFileStorageService.readSkillMd(selectedSkillId);
        List<String> supportFiles = skillFileStorageService.listSupportFiles(selectedSkillId);
        // Track which support files have already been injected to avoid re-loading
        Set<String> loadedSupportFiles = new LinkedHashSet<>();
        // Accumulate injected file sections for phaseN prompt
        StringBuilder injectedFilesSb = new StringBuilder();

        // Build MCP tool specifications (exclude skill tools; skill execution is driven by prompt flow)
        List<ToolSpecification> toolSpecs = buildToolSpecsWithThink(
                agent.getToolSpecifications().stream()
                        .filter(tool -> !tool.name().startsWith(SkillToolProvider.SKILL_TOOL_PREFIX))
                        .collect(Collectors.toList())
        );

        // Build base conversation: system prompt is fixed, user turns are appended per round
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = loadPrompt("prompts/system.jinja");
        String executorSystemPrompt = loadPrompt("prompts/executionSystemPrompt.jinja");
        messages.add(SystemMessage.from(systemPrompt + "\n" + executorSystemPrompt));

        String finalResult = "";
        int consecutiveNoActionRounds = 0;
        final int MAX_NO_ACTION_ROUNDS = 3;

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[ExecutionSubAgent] Skill Round {}/{} for skill: {}", round, maxRounds, selectedSkillId);

            // Build round-specific user message via phased templates
            String executionContext;
            if (round == 1) {
                executionContext = buildSkillPhase1Context(
                        plan, currentStep, completedSteps, skill, skillMdContent, supportFiles);
            } else {
                executionContext = buildSkillPhaseNContext(
                        plan, currentStep, completedSteps, skill, supportFiles,
                        loadedSupportFiles, injectedFilesSb.toString(), round);
            }

            List<ChatMessage> roundMessages = new ArrayList<>(messages);
            roundMessages.add(UserMessage.from(executionContext));

            // Call LLM with native MCP tool specifications
            ChatRequest request = ChatRequest.builder()
                    .messages(roundMessages)
                    .toolSpecifications(toolSpecs)
                    .build();

            log.info("[ExecutionSubAgent] Skill Round {} - LLM request (count={}): {}",
                    round, roundMessages.size(), renderMessageToLog(roundMessages));

            ChatResponse response;
            try {
                response = chatModel.chat(request);
            } catch (RateLimitException e) {
                log.error("[ExecutionSubAgent] Rate limit reached in skill round {}: {}", round, e.getMessage());
                throw e;
            } catch (InvalidRequestException e) {
                if (!isContextLengthExceeded(e)) {
                    throw e;
                }
                log.warn("[ExecutionSubAgent] Context length exceeded in skill round {}, compacting and retrying once", round);
                messages = compactExecutionMessages(messages);
                if (injectedFilesSb.length() > COMPACTED_CONTEXT_MAX_CHARS) {
                    String truncatedFiles = truncateForCompaction(injectedFilesSb.toString(), COMPACTED_CONTEXT_MAX_CHARS);
                    injectedFilesSb.setLength(0);
                    injectedFilesSb.append(truncatedFiles);
                }
                roundMessages = new ArrayList<>(messages);
                roundMessages.add(UserMessage.from(truncateForCompaction(executionContext, COMPACTED_CONTEXT_MAX_CHARS)));
                request = ChatRequest.builder()
                        .messages(roundMessages)
                        .toolSpecifications(toolSpecs)
                        .build();
                response = chatModel.chat(request);
            }
            AiMessage aiMessage = response.aiMessage();
            sendThinkingMessageEvent(aiMessage, agent, emitterOpt);
            String aiText = aiMessage.text();
            log.info("[ExecutionSubAgent] Skill Round {} - LLM response: {}", round, aiText);

            // Extract and send think block to frontend (also applies to tool-call rounds when text is present)
            if (aiText != null && !aiText.isEmpty()) {
                String thinkContent = extractThinkContent(aiText);
                if (thinkContent != null && !thinkContent.isEmpty()) {
                    log.info("[ExecutionSubAgent] Skill Round {} - Think block length: {}", round, thinkContent.length());
                    sendReasoningEvent(thinkContent, 0, agent, emitterOpt);
                }
            }

            // Persist AI message into conversation history
            messages.add(aiMessage);

            // --- Branch 1: Native MCP tool calls ---
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                log.info("[ExecutionSubAgent] Skill Round {} - {} MCP tool calls requested", round, toolRequests.size());

                for (ToolExecutionRequest toolRequest : toolRequests) {
                    String toolName = toolRequest.name();
                    String arguments = toolRequest.arguments();
                    log.info("[ExecutionSubAgent] Skill Round {} - tool call: {}, args: {}", round, toolName, arguments);

                    // Local think tool
                    if (THINK_TOOL_NAME.equals(toolName)) {
                        String thought = extractThought(arguments);
                        sendReasoningEvent(thought, 0, agent, emitterOpt);
                        messages.add(ToolExecutionResultMessage.from(toolRequest, "Thought logged."));
                        continue;
                    }

                    // Inject agentId for shell tools
                    ToolExecutionRequest finalToolRequest = toolRequest;
                    String finalArguments = arguments;
                    if (toolName.startsWith("shell_")) {
                        finalToolRequest = injectAgentIdForShellTool(toolRequest, agent.getAgentId());
                        finalArguments = finalToolRequest.arguments();
                    }

                    reportToolEvent(toolName, finalArguments, agent, emitterOpt);

                    String observation = executeToolWithRetry(toolName, finalToolRequest, agent);
                    log.info("[ExecutionSubAgent] Skill Round {} - Tool {} result: {}", round, toolName, observation);

                    messages.add(ToolExecutionResultMessage.from(toolRequest, observation));
                }

                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                String checkResult = checkStepCompletion(chatModel, messages, currentStep);
                if (checkResult != null) {
                    finalResult = checkResult;
                    log.info("[ExecutionSubAgent] Step completed after MCP tool execution in skill round {}", round);
                    break;
                }

                consecutiveNoActionRounds = 0;
                continue;
            }

            // --- Branch 2: Text-based action (READ_FILE / bash / COMPLETED) ---
            if (aiText == null || aiText.isEmpty()) {
                log.warn("[ExecutionSubAgent] Skill Round {} - AI message has neither text nor tool calls", round);
                break;
            }

            String processedText = stripThinkContent(aiText);

            // 1) COMPLETED
            if (processedText.contains("COMPLETED:")) {
                finalResult = processedText.substring(processedText.indexOf("COMPLETED:") + "COMPLETED:".length()).trim();
                log.info("[ExecutionSubAgent] Skill execution COMPLETED in round {}: {}", round, selectedSkillId);
                break;
            }

            // 2) READ_FILE instruction — load the requested file and inject in next round
            String requestedFile = extractReadFileInstruction(processedText);
            if (requestedFile != null) {
                if (loadedSupportFiles.contains(requestedFile)) {
                    log.warn("[ExecutionSubAgent] Skill Round {} - LLM re-requested already-loaded file: {}, skipping", round, requestedFile);
                    messages.add(UserMessage.from(
                            "[System] File '" + requestedFile + "' was already loaded in a previous round. "
                            + "Please proceed using the file content already provided."));
                } else if (!supportFiles.contains(requestedFile)) {
                    log.warn("[ExecutionSubAgent] Skill Round {} - LLM requested unknown file: {}", round, requestedFile);
                    messages.add(UserMessage.from(
                            "[System] File '" + requestedFile + "' does not exist in this skill. "
                            + "Available files: " + String.join(", ", supportFiles)));
                } else {
                    String fileSection = skillFileStorageService.formatSupportFile(selectedSkillId, requestedFile);
                    loadedSupportFiles.add(requestedFile);
                    injectedFilesSb.append(fileSection).append("\n");
                    log.info("[ExecutionSubAgent] Skill Round {} - Loaded support file: {}", round, requestedFile);
                    messages.add(UserMessage.from(
                            "[System] File content loaded as requested:\n\n" + fileSection));
                }
                consecutiveNoActionRounds = 0;
                continue;
            }

            // 3) Shell command — execute it
            String command = extractCommand(processedText);
            if (command != null && !command.isEmpty()) {
                log.info("[ExecutionSubAgent] Executing skill command: {}", command);

                JSONObject skillArgs = new JSONObject();
                skillArgs.put("command", command);
                reportToolEvent("shell_skill_execute", skillArgs.toJSONString(), agent, emitterOpt);

                String observation = executeSkillCommand(selectedSkillId, sessionId, command, agent);
                log.info("[ExecutionSubAgent] Skill command result: {}", observation);

                messages.add(UserMessage.from("Command output:\n" + observation));

                // Check completion after command
                String checkResult = checkStepCompletion(chatModel, messages, currentStep);
                if (checkResult != null) {
                    finalResult = checkResult;
                    log.info("[ExecutionSubAgent] Step completed after command execution in round {}", round);
                    break;
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                consecutiveNoActionRounds = 0;
                continue;
            }

            // 4) No recognizable action — treat as text result
            finalResult = processedText;
            consecutiveNoActionRounds++;
            log.info("[ExecutionSubAgent] Skill Round {} - No action detected (consecutive: {})",
                    round, consecutiveNoActionRounds);

            String checkResult = checkStepCompletion(chatModel, messages, currentStep);
            if (checkResult != null) {
                finalResult = checkResult;
                break;
            }

            if (consecutiveNoActionRounds >= MAX_NO_ACTION_ROUNDS) {
                log.warn("[ExecutionSubAgent] Breaking skill loop after {} no-action rounds for skill: {}",
                        consecutiveNoActionRounds, selectedSkillId);
                break;
            }
        }

        if (finalResult.isEmpty()) {
            finalResult = "Skill execution completed for: " + selectedSkillId;
        }

        return finalResult;
    }

    /**
     * Extract READ_FILE instruction from LLM response.
     * Supports formats:
     *   READ_FILE: scripts/run.sh
     *   ```\nREAD_FILE: scripts/run.sh\n```
     *
     * @return the requested relative path, or null if no READ_FILE instruction found
     */
    private String extractReadFileInstruction(String text) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("READ_FILE:")) {
                String path = trimmed.substring("READ_FILE:".length()).trim();
                // Strip surrounding backticks if any
                path = path.replace("`", "").trim();
                if (!path.isEmpty()) {
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * Build context for the first skill execution round.
     * Injects: SKILL.md full content + support file directory listing.
     */
    private String buildSkillPhase1Context(Plan plan,
                                            Step currentStep,
                                            List<Step> completedSteps,
                                            SkillDefinitionDTO skill,
                                            String skillMdContent,
                                            List<String> supportFiles) throws IOException {
        String template = loadPrompt("prompts/skillExecutionPhase1.jinja");
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("goal", plan.getGoal());
        ctx.put("skillId", skill.getSkillId());
        ctx.put("skillName", skill.getName());
        ctx.put("skillDescription", skill.getDescription());
        ctx.put("skillMdContent", skillMdContent.isEmpty() ? "(SKILL.md not found)" : skillMdContent);
        ctx.put("supportFileList", buildSupportFileList(supportFiles, Collections.emptySet()));
        ctx.put("completedStepsSection", buildCompletedStepsSection(completedSteps, 200));
        ctx.put("currentStepDescription", currentStep.getDescription());
        return render(template, ctx);
    }

    /**
     * Build context for subsequent skill execution rounds.
     * Injects: previously loaded support files + remaining file list (excluding already-loaded).
     */
    private String buildSkillPhaseNContext(Plan plan,
                                            Step currentStep,
                                            List<Step> completedSteps,
                                            SkillDefinitionDTO skill,
                                            List<String> supportFiles,
                                            Set<String> loadedSupportFiles,
                                            String injectedFilesContent,
                                            int round) throws IOException {
        String template = loadPrompt("prompts/skillExecutionPhaseN.jinja");

        // Build injected files section header
        String injectedFilesSection = "";
        if (!injectedFilesContent.isEmpty()) {
            injectedFilesSection = "## Loaded Support Files\n" + injectedFilesContent;
        }

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("goal", plan.getGoal());
        ctx.put("skillId", skill.getSkillId());
        ctx.put("skillName", skill.getName());
        ctx.put("skillDescription", skill.getDescription());
        ctx.put("injectedFilesSection", injectedFilesSection);
        ctx.put("supportFileList", buildSupportFileList(supportFiles, loadedSupportFiles));
        ctx.put("completedStepsSection", buildCompletedStepsSection(completedSteps, 200));
        ctx.put("currentStepDescription", currentStep.getDescription());
        ctx.put("currentRound", String.valueOf(round));
        return render(template, ctx);
    }

    /**
     * Build formatted support file list, marking already-loaded files.
     */
    private String buildSupportFileList(List<String> supportFiles, Set<String> loadedFiles) {
        if (supportFiles.isEmpty()) {
            return "(No support files available)\n";
        }
        StringBuilder sb = new StringBuilder();
        for (String f : supportFiles) {
            if (loadedFiles.contains(f)) {
                sb.append("- ").append(f).append(" [already loaded]\n");
            } else {
                sb.append("- ").append(f).append("\n");
            }
        }
        return sb.toString();
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
                .map(tool -> skillToolProvider.parseSkillIdFromToolName(tool.name()))
                .filter(Objects::nonNull)
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
     * Extract content from <think>...</think> block in AI response.
     * @return the thinking content, or null if no think block found
     */
    private String extractThinkContent(String text) {
        if (text == null) return null;
        int start = text.indexOf("<think>");
        int end = text.indexOf("</think>");
        if (start >= 0 && end > start) {
            return text.substring(start + 7, end).trim();
        }
        return null;
    }

    /**
     * Strip <think>...</think> block from AI response text.
     * @return text with think block removed
     */
    private String stripThinkContent(String text) {
        if (text == null) return "";
        return text.replaceAll("<think>[\\s\\S]*?</think>", "").trim();
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
        } catch (InvalidRequestException e) {
            if (!isContextLengthExceeded(e)) {
                log.warn("[ExecutionSubAgent] Failed to check step completion: {}", e.getMessage());
                return null;
            }
            log.warn("[ExecutionSubAgent] Context length exceeded during completion check, compacting and retrying once");
            try {
                List<ChatMessage> compactedMessages = compactExecutionMessages(messages);
                compactedMessages.add(UserMessage.from(checkPrompt));
                ChatResponse checkResponse = chatModel.chat(ChatRequest.builder()
                        .messages(compactedMessages)
                        .build());
                AiMessage checkAiMessage = checkResponse.aiMessage();
                String responseText = checkAiMessage.text();
                if (responseText != null && responseText.startsWith("COMPLETED:")) {
                    messages.clear();
                    messages.addAll(compactedMessages);
                    messages.add(checkAiMessage);
                    return responseText.substring("COMPLETED:".length()).trim();
                }
            } catch (Exception retryException) {
                log.warn("[ExecutionSubAgent] Failed to retry completion check after compaction: {}", retryException.getMessage());
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
     * Perform a deep thinking step before execution.
     * Uses the model's native reasoning capability to analyze the current step,
     * then sends the thinking content to the frontend as reasoning content.
     */
    private void performDeepThinking(ChatModel chatModel,
                                      Plan plan,
                                      Step currentStep,
                                      List<Step> completedSteps,
                                      SseEmitter emitter,
                                      Agent agent) {
        log.info("[ExecutionSubAgent] Performing deep thinking for step: {}", currentStep.getDescription());

        try {
            // Build thinking prompt
            StringBuilder thinkingPrompt = new StringBuilder();
            thinkingPrompt.append("You are about to execute a step in a plan. Before taking any action, think deeply about:\n");
            thinkingPrompt.append("1. What is the goal of this step?\n");
            thinkingPrompt.append("2. What approach should be taken?\n");
            thinkingPrompt.append("3. What potential challenges or edge cases might arise?\n");
            thinkingPrompt.append("4. What is the best strategy to accomplish this step?\n\n");
            thinkingPrompt.append("## Overall Goal\n").append(plan.getGoal()).append("\n\n");
            thinkingPrompt.append("## Current Step\n").append(currentStep.getDescription()).append("\n\n");

            if (completedSteps != null && !completedSteps.isEmpty()) {
                thinkingPrompt.append("## Previously Completed Steps\n");
                for (Step step : completedSteps) {
                    thinkingPrompt.append("- ").append(step.getDescription());
                    if (StringUtils.isNotBlank(step.getResult())) {
                        thinkingPrompt.append(" (Result: ").append(step.getResult(), 0, Math.min(step.getResult().length(), 200)).append(")");
                    }
                    thinkingPrompt.append("\n");
                }
                thinkingPrompt.append("\n");
            }

            thinkingPrompt.append("Please provide your deep analysis and reasoning about how to execute this step. ");
            thinkingPrompt.append("Focus on strategy, approach, and potential issues.");

            // Call LLM for deep thinking
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from("You are a deep thinking assistant. Analyze the given task and provide thorough reasoning about how to approach it. Be concise but insightful."));
            messages.add(UserMessage.from(thinkingPrompt.toString()));

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .build();

            long startTime = System.currentTimeMillis();
            ChatResponse response = chatModel.chat(request);
            long thinkTime = System.currentTimeMillis() - startTime;

            AiMessage aiMessage = response.aiMessage();
            String thinkingContent = aiMessage.text();

            if (StringUtils.isNotBlank(thinkingContent)) {
                log.info("[ExecutionSubAgent] Deep thinking completed in {}ms, content length: {}",
                        thinkTime, thinkingContent.length());

                // Send thinking content to frontend as reasoning content
                sendReasoningEvent(thinkingContent, thinkTime, agent, emitter);
            } else {
                log.warn("[ExecutionSubAgent] Deep thinking returned empty content");
            }

        } catch (Exception e) {
            log.warn("[ExecutionSubAgent] Deep thinking failed, continuing with execution: {}", e.getMessage());
            // Deep thinking failure should not block execution
        }
    }

    /**
     * Send a reasoning/thinking SSE event to the frontend.
     */
    private void sendReasoningEvent(String reasoningContent, long thinkTime, Agent agent, SseEmitter emitter) {
        MessageEventData messageEventData = new MessageEventData();
        messageEventData.setTimestamp(System.currentTimeMillis());
        messageEventData.setReasoningContent(reasoningContent);
        messageEventData.setThinkTime(thinkTime);

        try {
            sendSseEvent(agent, emitter, SSEEventType.MESSAGE.getType(), JSON.toJSONString(messageEventData));
        } catch (Exception e) {
            log.error("[ExecutionSubAgent] Failed to send reasoning SSE event: agentId={}", agent.getAgentId(), e);
        }

        // Persist reasoning content
        conversationHistoryService.saveAssistantMessageWithId(
                "**Deep Thinking:**\n" + reasoningContent, SSEEventType.MESSAGE,
                agent.getUserId(), agent.getAgentId());
    }

    private void sendThinkingMessageEvent(AiMessage aiMessage, Agent agent, SseEmitter emitter) {
        if (aiMessage == null || StringUtils.isBlank(aiMessage.thinking())) {
            return;
        }
        sendReasoningEvent(aiMessage.thinking(), 0, agent, emitter);
    }

    private List<ChatMessage> compactExecutionMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        List<ChatMessage> compacted = new ArrayList<>();
        compacted.add(messages.get(0));

        int startIndex = Math.max(1, messages.size() - 20);
        StringBuilder summary = new StringBuilder();
        summary.append("The execution context was compacted because it exceeded the model context limit.\n");
        summary.append("Continue the current task from this compacted recent context:\n\n");
        for (int i = startIndex; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            summary.append("## ").append(message.type()).append("\n");
            summary.append(truncateForCompaction(extractMessageText(message), 8_000)).append("\n\n");
            if (summary.length() > COMPACTED_CONTEXT_MAX_CHARS) {
                break;
            }
        }

        compacted.add(UserMessage.from(truncateForCompaction(summary.toString(), COMPACTED_CONTEXT_MAX_CHARS)));
        return compacted;
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

    private String extractMessageText(ChatMessage message) {
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
            sendSseEvent(agent, emitter, SSEEventType.TOOL.getType(), toolEventData);
        } catch (Exception e) {
            log.error("Failed to send SSE tool event: agentId={}", agent.getAgentId(), e);
        }

        // Persist
        conversationHistoryService.saveAssistantMessageWithId(
                JSON.toJSONString(toolEventData), SSEEventType.TOOL,
                agent.getUserId(), agent.getAgentId());
    }

    private void sendSseEvent(Agent agent, SseEmitter fallbackEmitter, String eventName, Object data) throws IOException {
        AgentSession agentSession = globalAgentSessionManager.getSession(agent.getAgentId());
        if (agentSession != null && agentSession.isFrontendConnected()) {
            agentSession.sendMessage(eventName, data);
            return;
        }
        if (fallbackEmitter != null) {
            fallbackEmitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data)
                    .id(String.valueOf(System.currentTimeMillis())));
        }
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
