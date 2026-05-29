package cn.nolaurene.cms.common.sandbox.backend.req;

import lombok.Data;

/**
 * Manus 对话DTO
 */
@Data
public class ChatRequest {

    private String message;

    private Long timestamp;

    /**
     * When true, execute the legacy plan-act loop. Default false uses the skill-based agent loop.
     */
    private Boolean planMode;
  
    // optional: identify user and session for persistence
    private String userId;
  
    private String sessionId;

//    public ChatRequest(String rawJSONString) {
//        ChatRequest chatRequest = JSON.parseObject(rawJSONString, ChatRequest.class);
//        this.message = chatRequest.getMessage();
//        this.timestamp = chatRequest.getTimestamp();
//    }
}
