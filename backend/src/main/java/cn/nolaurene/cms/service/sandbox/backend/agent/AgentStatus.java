package cn.nolaurene.cms.service.sandbox.backend.agent;


import lombok.Getter;

/**
 * @author nolaurence
 * @date 2025/8/6 下午5:06
 * @description:
 */
public enum AgentStatus {
    IDLE("idle"),
    PLANNING("planning"),
    EXECUTING("executing"),
    CONCLUDING("concluding"),
    COMPLETED("completed"),
    UPDATING("updating");


    @Getter
    private final String code;

    AgentStatus(String code) {
        this.code = code;
    }
}
