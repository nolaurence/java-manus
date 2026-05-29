package cn.nolaurene.cms.common.sandbox.backend.llm;


import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
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

    /** reasoning/thinking content from DeepSeek R1 or similar models */
    private String thinking;

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
                if (eventType == SSEEventType.TOOL) {
                    cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData toolData =
                            com.alibaba.fastjson.JSON.parseObject(content, cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData.class);
                    String result = toolData.getResult();
                    if (result == null || result.isEmpty()) {
                        result = "(no result)";
                    }
                    String toolDesc = "[Tool] " + toolData.getFunction() + "\nResult: " + result;
                    return AiMessage.builder().text(toolDesc).build();
                }
                if (eventType == SSEEventType.COMPACT) {
                    // Compaction summary is already formatted as a summary text
                    return AiMessage.builder().text(content != null ? content : "").build();
                }
                AiMessage.Builder builder = AiMessage.builder()
                        .text(content != null ? content : "");
                if (thinking != null && !thinking.isEmpty()) {
                    builder.thinking(thinking);
                }
                return builder.build();
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
            AiMessage aiMsg = (AiMessage) msg;
            ChatMessage chatMsg = new ChatMessage(Role.assistant, eventType, aiMsg.text());
            if (aiMsg.thinking() != null && !aiMsg.thinking().isEmpty()) {
                chatMsg.setThinking(aiMsg.thinking());
            }
            return chatMsg;
        }
        throw new IllegalArgumentException("Unsupported langchain4j message type: " + msg.getClass());
    }
}
