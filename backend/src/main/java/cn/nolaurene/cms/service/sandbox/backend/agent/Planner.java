package cn.nolaurene.cms.service.sandbox.backend.agent;


import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMemory;
import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.model.data.StepEventStatus;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer;
import cn.nolaurene.cms.service.sandbox.backend.llm.LlmClient;
import cn.nolaurene.cms.service.sandbox.backend.utils.ReActParser;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.removeSystemPrompt;

/**
 * @author nolaurence
 * @date 2025/8/6 下午5:04
 * @description: planner to generate plan for given task
 */
@Slf4j
public class Planner {

    private static String PLANNER_PRIMARY_PROMPT;

    public Planner() {
        try {
            loadSystemPrompt();
        } catch (IOException e) {
            log.error("Failed to load planner system prompt", e);
            throw new RuntimeException("Failed to load planner system prompt", e);
        }
    }

    public void loadSystemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/planner.jinja");
        InputStream inputStream = resource.getInputStream();
        PLANNER_PRIMARY_PROMPT = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }


    public String createPlan(LlmClient llmClient, List<ChatMessage> memory) throws IOException {
        List<ChatMessage> userMessageList = removeSystemPrompt(memory);

        // load create plan prompt
        ClassPathResource resource = new ClassPathResource("prompts/createPlan.jinja");
        InputStream inputStream = resource.getInputStream();
        String createPlanPromptTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        List<String> systemPromptList = new ArrayList<>();
        systemPromptList.add(PLANNER_PRIMARY_PROMPT);
        systemPromptList.add(createPlanPromptTemplate);

        // add system prompt
        List<ChatMessage> messageList = new ArrayList<>();
        messageList.add(new ChatMessage(ChatMessage.Role.system, String.join("\n\n", systemPromptList)));

        messageList.addAll(userMessageList);

        log.info("[Planner] create plan request: {}", JSON.toJSONString(messageList));

        String llmResponse = llmClient.chat(messageList);
        return ReActParser.parseOpenAIStyleResponse(llmResponse);
    }

    public String updatePlan(LlmClient llmClient, ChatMemory memory, Plan plan) throws IOException {

        ClassPathResource resource = new ClassPathResource("prompts/updatePlan.jinja");
        InputStream inputStream = resource.getInputStream();
        String updatePlanTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        Map<String, Object> context = new HashMap<>();
        context.put("stepResult", JSON.toJSONString(keepLatestResult(plan.getSteps())));
        context.put("steps", JSON.toJSONString(removeResultDetail(plan.getSteps())));
        context.put("goal", plan.getGoal());

        String updatePlanPrompt = PromptRenderer.render(updatePlanTemplate, context);

        // ask llm
        List<ChatMessage> messageList = new ArrayList<>(memory.getHistory());
        messageList.add(new ChatMessage(ChatMessage.Role.user, updatePlanPrompt));

        log.info("[Planner] update plan request: {}", JSON.toJSONString(messageList));
        String llmResponse = llmClient.chat(messageList);
        return ReActParser.parseOpenAIStyleResponse(llmResponse);
    }

    private List<Step> keepLatestResult(List<Step> stepList) {
        // deep copy
        List<Step> newStepList = stepList.stream().map(step -> {
            Step newStep = new Step();
            newStep.setId(step.getId());
            newStep.setDescription(step.getDescription());
            newStep.setStatus(step.getStatus());
            newStep.setResult(step.getResult());
            newStep.setError(step.getError());

            return newStep;
        }).collect(Collectors.toList());

        int lastResultIdx = 0;
        for (int i = newStepList.size() - 1; i >= 0; i--) {
            Step step = newStepList.get(i);
            if (StepEventStatus.completed.getCode().equals(step.getStatus())) {
                lastResultIdx = i;
                break;
            }
        }
        for (int idx = 0; idx < lastResultIdx; idx++) {
            newStepList.get(idx).setResult(null);
            newStepList.get(idx).setError(null);
        }
        return newStepList;
    }

    private List<Step> removeResultDetail(List<Step> stepList) {
        return stepList.stream().map(step -> {
            Step newStep = new Step();
            newStep.setId(step.getId());
            newStep.setDescription(step.getDescription());
            newStep.setStatus(step.getStatus());
            // leave result and error empty

            return newStep;
        }).collect(Collectors.toList());
    }
}
