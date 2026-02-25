package cn.nolaurene.cms.service.sandbox.backend.utils;

import cn.nolaurene.cms.service.sandbox.backend.message.Plan;
import cn.nolaurene.cms.service.sandbox.backend.message.Step;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public class ReActParser {

    public static String parseThinking(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }

        Pattern thinkPattern = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
        Matcher thinkMatcher = thinkPattern.matcher(content);

        if (thinkMatcher.find()) {
            return thinkMatcher.group(1).trim();
        }
        return "";
    }

    public static String parseStringOfTag(String content, String tagName) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        Pattern goalPattern = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">", Pattern.DOTALL);
        Matcher goalMatcher = goalPattern.matcher(content);

        if (goalMatcher.find()) {
            return goalMatcher.group(1).trim();
        }
        return "";
    }

    public static List<Step> parseStepListFromContent(String content) {
        List<Step> toolCalls = new ArrayList<>();
        Pattern stepsPattern = Pattern.compile("<step>(.*?)</step>", Pattern.DOTALL);
        Matcher stepsMatcher = stepsPattern.matcher(content);

        while (stepsMatcher.find()) {
            String stepBlock = stepsMatcher.group(1);

            Pattern idPattern = Pattern.compile("<id>(.*?)</id>");
            Pattern descriptionPattern = Pattern.compile("<description>(.*?)</description>", Pattern.DOTALL);

            Matcher idMatcher = idPattern.matcher(stepBlock);
            Matcher descriptionMatcher = descriptionPattern.matcher(stepBlock);

            String id = idMatcher.find() ? idMatcher.group(1).trim() : null;
            String description = descriptionMatcher.find() ? descriptionMatcher.group(1).trim() : null;

            if (id != null && description != null) {
                Step step = new Step();
                step.setId(Integer.valueOf(id));
                step.setDescription(description);
                toolCalls.add(step);
            }
        }
        return toolCalls;
    }

    public static Plan parsePlan(String content) {
        String planString = parseStringOfTag(content, "plan");
        String message = parseStringOfTag(planString, "message");
        String goal = parseStringOfTag(planString, "goal");
        String title = parseStringOfTag(planString, "title");
        List<Step> steps = parseStepListFromContent(planString);

        Plan plan = new Plan();
        plan.setMessage(message);
        plan.setGoal(goal);
        plan.setTitle(title);
        plan.setSteps(steps);

        return plan;
    }

    public static List<String> parseStepDescriptions(String xmlContent) {
        List<Step> steps = parseStepListFromContent(xmlContent);
        return steps.stream().map(Step::getDescription).collect(Collectors.toList());
    }
}
