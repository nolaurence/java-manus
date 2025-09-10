package cn.nolaurene.cms.common.sandbox.backend.req;

import lombok.Data;

/**
 * Manus 对话DTO
 */
@Data
public class ChatRequest {

    private String message;

    private Long timestamp;

    // optional: identify user and session for persistence
    private String userId;

    // optional: default to agentId if not provided
    private String sessionId;

//    public ChatRequest(String rawJSONString) {
//        ChatRequest chatRequest = JSON.parseObject(rawJSONString, ChatRequest.class);
//        this.message = chatRequest.getMessage();
//        this.timestamp = chatRequest.getTimestamp();
//    }
}
