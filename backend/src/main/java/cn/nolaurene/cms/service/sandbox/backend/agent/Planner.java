package cn.nolaurene.cms.service.sandbox.backend.agent;


import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMemory;
import cn.nolaurene.cms.common.sandbox.backend.model.data.StepEventStatus;
import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.*;

/**
 * @author nolaurence
 * @date 2025/8/6 下午5:04
 * @description: planner to generate plan for given task
 */
@Slf4j
public class Planner {

    public String createPlan(ChatModel chatModel, String userInput, ChatMemory memory) throws IOException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(loadPrompt("prompts/system.jinja")));
        messages.addAll(memory.toLangchain4jMessages());
        messages.add(UserMessage.from(loadPrompt("prompts/createPlan.jinja")));
        messages.add(UserMessage.from(userInput));

        log.info("[Planner] create plan request, messages count: {}", messages.size());

        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
        return response.aiMessage().text();
    }

    public String updatePlan(ChatModel chatModel, ChatMemory memory, Plan plan) throws IOException {

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(loadPrompt("prompts/system.jinja")));
        messages.addAll(memory.toLangchain4jMessages());

        // assemble update prompt
        String updatePlanTemplate = loadPrompt("prompts/updatePlan.jinja");
        Map<String, Object> context = new HashMap<>();
        context.put("stepResult", JSON.toJSONString(keepLatestResult(plan.getSteps())));
        context.put("steps", JSON.toJSONString(removeResultDetail(plan.getSteps())));
        context.put("goal", plan.getGoal());

        String updatePlanPrompt = render(updatePlanTemplate, context);

        messages.add(UserMessage.from(updatePlanPrompt));

        log.info("[Planner] update plan request, messages count: {}", messages.size());
        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
        return response.aiMessage().text();
    }

    private List<Step> keepLatestResult(List<Step> stepList) {
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
            return newStep;
        }).collect(Collectors.toList());
    }
}
