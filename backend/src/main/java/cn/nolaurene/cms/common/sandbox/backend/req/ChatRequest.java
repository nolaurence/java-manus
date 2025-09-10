package cn.nolaurene.cms.common.sandbox.backend.req;

import lombok.Data;

/**
 * Manus 对话DTO
 */
@Data
public class ChatRequest {

    private String message;

    private Long timestamp;

    private String userId;

    private String sessionId;

//    public ChatRequest(String rawJSONString) {
//        ChatRequest chatRequest = JSON.parseObject(rawJSONString, ChatRequest.class);
//        this.message = chatRequest.getMessage();
//        this.timestamp = chatRequest.getTimestamp();
//    }
}
