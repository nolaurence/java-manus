package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.List;

/**
 * Skill 路由决策结果
 * 用于存储大模型对当前步骤的工具选择决策
 *
 * @author nolaurence
 */
@Data
public class SkillRoutingDecision {

    /**
     * 决策类型：DIRECT_TOOL - 直接使用 MCP 工具，SKILL - 使用 Skill
     */
    private String decisionType;

    /**
     * 选择的工具/Skill 名称
     */
    private String selectedTool;

    /**
     * 选择原因
     */
    private String reason;

    /**
     * 如果是 DIRECT_TOOL，存储要使用的 MCP 工具列表
     */
    private List<String> directTools;

    /**
     * 如果是 SKILL，存储要使用的 Skill ID
     */
    private String skillId;

    /**
     * 执行参数
     */
    private java.util.Map<String, Object> params;

    public static final String DECISION_TYPE_DIRECT_TOOL = "DIRECT_TOOL";
    public static final String DECISION_TYPE_SKILL = "SKILL";

    /**
     * 判断是否为直接使用工具
     */
    public boolean isDirectTool() {
        return DECISION_TYPE_DIRECT_TOOL.equals(decisionType);
    }

    /**
     * 判断是否为使用 Skill
     */
    public boolean isSkill() {
        return DECISION_TYPE_SKILL.equals(decisionType);
    }
}
