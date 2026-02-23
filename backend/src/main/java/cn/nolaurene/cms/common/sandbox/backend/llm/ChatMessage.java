package cn.nolaurene.cms.common.sandbox.backend.llm;


import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
@Getter
@Setter
public class ChatMessage {

    public enum Role { system, user, assistant }

    private Role role;

    private String content;

    private SSEEventType eventType;

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.eventType = SSEEventType.MESSAGE;
        this.content = content;
    }

    public ChatMessage(Role role, SSEEventType eventType, String content) {
        this.role = role;
        this.eventType = eventType;
        this.content = content;
    }
}
