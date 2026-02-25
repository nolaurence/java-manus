package cn.nolaurene.cms.common.sandbox.backend.llm;


import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
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

    public enum Role { system, user, assistant, tool }

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

    /**
     * Convert to langchain4j message type.
     */
    public dev.langchain4j.data.message.ChatMessage toLangchain4j() {
        switch (role) {
            case system:
                return SystemMessage.from(content != null ? content : "");
            case user:
                return UserMessage.from(content != null ? content : "");
            case assistant:
                return AiMessage.from(content != null ? content : "");
            default:
                throw new IllegalStateException("Unsupported role for langchain4j conversion: " + role);
        }
    }

    /**
     * Create ChatMessage from a langchain4j message.
     */
    public static ChatMessage fromLangchain4j(dev.langchain4j.data.message.ChatMessage msg, SSEEventType eventType) {
        if (msg instanceof SystemMessage) {
            return new ChatMessage(Role.system, eventType, ((SystemMessage) msg).text());
        }
        if (msg instanceof UserMessage) {
            return new ChatMessage(Role.user, eventType, ((UserMessage) msg).singleText());
        }
        if (msg instanceof AiMessage) {
            return new ChatMessage(Role.assistant, eventType, ((AiMessage) msg).text());
        }
        throw new IllegalArgumentException("Unsupported langchain4j message type: " + msg.getClass());
    }
}
