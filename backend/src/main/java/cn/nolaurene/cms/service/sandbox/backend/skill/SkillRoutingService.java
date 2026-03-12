package cn.nolaurene.cms.service.sandbox.backend.skill;

import cn.nolaurene.cms.common.dto.skill.SkillRoutingDecision;
import cn.nolaurene.cms.common.sandbox.backend.model.Agent;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.loadPrompt;

/**
 * Skill 路由服务
 * 在 executionSubagent 前添加分流功能，让大模型决定使用 Skill 还是直接使用 MCP 工具
 *
 * @author nolaurence
 */
@Slf4j
@Service
public class SkillRoutingService {

    private static final String ROUTING_PROMPT_PATH = "prompts/skillRouting.jinja";

    /**
     * 执行路由决策
     *
     * @param chatModel     LLM 模型
     * @param agent         Agent 上下文
     * @param plan          当前计划
     * @param currentStep   当前步骤
     * @param completedSteps 已完成步骤
     * @return 路由决策结果
     */
    public SkillRoutingDecision route(
            ChatModel chatModel,
            Agent agent,
            Plan plan,
            Step currentStep,
            List<Step> completedSteps) {

        log.info("[SkillRoutingService] Starting routing decision for step: {}", currentStep.getDescription());

        // 构建路由上下文消息
        List<ChatMessage> messages = new ArrayList<>();
        try {
            messages.add(SystemMessage.from(loadPrompt(ROUTING_PROMPT_PATH)));
        } catch (IOException e) {
            log.error("[SkillRoutingService] Failed to load routing prompt: {}", e.getMessage());
            return createDefaultDirectToolDecision();
        }

        // 构建路由请求上下文
        String routingContext = buildRoutingContext(agent, plan, currentStep, completedSteps);
        messages.add(UserMessage.from(routingContext));

        try {
            // 调用 LLM 进行路由决策
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .build();

            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();

            String responseText = aiMessage.text();
            log.info("[SkillRoutingService] LLM routing response: {}", responseText);

            // 解析决策结果
            SkillRoutingDecision decision = parseRoutingDecision(responseText);

            if (decision != null) {
                log.info("[SkillRoutingService] Routing decision: type={}, tool/skill={}",
                        decision.getDecisionType(), decision.getSelectedTool());
            } else {
                log.warn("[SkillRoutingService] Failed to parse routing decision, defaulting to DIRECT_TOOL");
                decision = createDefaultDirectToolDecision();
            }

            return decision;

        } catch (Exception e) {
            log.error("[SkillRoutingService] Error during routing decision: {}", e.getMessage(), e);
            // 出错时默认使用 DIRECT_TOOL
            return createDefaultDirectToolDecision();
        }
    }

    /**
     * 构建路由上下文
     */
    private String buildRoutingContext(Agent agent, Plan plan, Step currentStep, List<Step> completedSteps) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Goal\n");
        sb.append(plan.getGoal()).append("\n\n");

        sb.append("## Available Tools\n");

        // MCP 工具
        sb.append("### MCP Tools (Direct Execution):\n");
        List<ToolSpecification> mcpTools = agent.getToolSpecifications().stream()
                .filter(tool -> !tool.name().startsWith(SkillToolProvider.SKILL_TOOL_PREFIX))
                .collect(Collectors.toList());

        for (ToolSpecification tool : mcpTools) {
            sb.append("- ").append(tool.name());
            if (tool.description() != null) {
                sb.append(": ").append(tool.description().split("\n")[0]); // 只取第一行
            }
            sb.append("\n");
        }

        // Skill 工具
        sb.append("\n### Available Skills:\n");
        List<ToolSpecification> skillTools = agent.getToolSpecifications().stream()
                .filter(tool -> tool.name().startsWith(SkillToolProvider.SKILL_TOOL_PREFIX))
                .collect(Collectors.toList());

        for (ToolSpecification tool : skillTools) {
            String skillId = tool.name().substring(SkillToolProvider.SKILL_TOOL_PREFIX.length());
            sb.append("- ").append(skillId);
            if (tool.description() != null) {
                String desc = tool.description().split("\n")[0];
                sb.append(": ").append(desc);
            }
            sb.append("\n");
        }

        // 已完成步骤
        if (completedSteps != null && !completedSteps.isEmpty()) {
            sb.append("\n## Completed Steps\n");
            for (Step step : completedSteps) {
                sb.append("- ").append(step.getDescription());
                if (step.getResult() != null) {
                    String truncated = step.getResult().length() > 100
                            ? step.getResult().substring(0, 100) + "..."
                            : step.getResult();
                    sb.append(" -> ").append(truncated);
                }
                sb.append("\n");
            }
        }

        // 当前步骤
        sb.append("\n## Current Step to Route\n");
        sb.append(currentStep.getDescription()).append("\n\n");

        sb.append("## Instructions\n");
        sb.append("Analyze the current step and decide whether to use DIRECT MCP tools or a SKILL.\n");
        sb.append("Respond with a JSON object containing your routing decision.\n");
        sb.append("Remember: Use DIRECT_TOOL for simple tasks, SKILL for complex tasks.\n");

        return sb.toString();
    }

    /**
     * 解析路由决策
     */
    private SkillRoutingDecision parseRoutingDecision(String responseText) {
        try {
            // 尝试提取 JSON 部分
            String jsonStr = extractJson(responseText);
            if (jsonStr == null) {
                return null;
            }

            JSONObject json = JSON.parseObject(jsonStr);
            SkillRoutingDecision decision = new SkillRoutingDecision();

            decision.setDecisionType(json.getString("decisionType"));
            decision.setSelectedTool(json.getString("selectedTool"));
            decision.setReason(json.getString("reason"));
            decision.setSkillId(json.getString("skillId"));

            // 解析 directTools 列表
            if (json.containsKey("directTools")) {
                decision.setDirectTools(json.getList("directTools", String.class));
            }

            // 解析 params
            if (json.containsKey("params")) {
                decision.setParams(JSON.parseObject(json.getJSONObject("params").toJSONString(), new TypeReference<Map<String, Object>>() {}));
            }

            // 验证决策类型
            if (!SkillRoutingDecision.DECISION_TYPE_DIRECT_TOOL.equals(decision.getDecisionType())
                    && !SkillRoutingDecision.DECISION_TYPE_SKILL.equals(decision.getDecisionType())) {
                log.warn("[SkillRoutingService] Invalid decision type: {}", decision.getDecisionType());
                return null;
            }

            return decision;

        } catch (Exception e) {
            log.error("[SkillRoutingService] Failed to parse routing decision: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从响应文本中提取 JSON
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 尝试直接解析
        text = text.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }

        // 尝试从代码块中提取
        if (text.contains("```json")) {
            int start = text.indexOf("```json") + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // 尝试从 ``` 代码块中提取
        if (text.contains("```")) {
            int start = text.indexOf("```") + 3;
            int end = text.indexOf("```", start);
            if (end > start) {
                String content = text.substring(start, end).trim();
                if (content.startsWith("{") && content.endsWith("}")) {
                    return content;
                }
            }
        }

        // 尝试找到 JSON 对象
        int jsonStart = text.indexOf("{");
        int jsonEnd = text.lastIndexOf("}");
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1);
        }

        return null;
    }

    /**
     * 创建默认的直接工具决策
     */
    private SkillRoutingDecision createDefaultDirectToolDecision() {
        SkillRoutingDecision decision = new SkillRoutingDecision();
        decision.setDecisionType(SkillRoutingDecision.DECISION_TYPE_DIRECT_TOOL);
        decision.setSelectedTool("default");
        decision.setReason("Default decision due to routing error or unclear task");
        decision.setDirectTools(new ArrayList<>());
        return decision;
    }
}
