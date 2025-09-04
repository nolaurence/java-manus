package cn.nolaurene.cms.common.sandbox.backend.llm;


import lombok.Data;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
@Data
public class ChatMessage {

    public enum Role { system, user, assistant }

    private final Role role;

    private final String content;

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }
}
