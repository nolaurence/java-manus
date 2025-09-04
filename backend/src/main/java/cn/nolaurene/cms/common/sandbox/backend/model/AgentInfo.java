package cn.nolaurene.cms.common.sandbox.backend.model;

import lombok.Data;

/**
 * Agent的基本信息
 */
@Data
public class AgentInfo {

    private String agentId;

    private String status;

    private String message;
}
