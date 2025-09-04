package cn.nolaurene.cms.common.sandbox.backend.llm;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public class ChatMemory {

    private final List<ChatMessage> history = new ArrayList<>();

    public void add(ChatMessage message) {
        history.add(message);
    }

    public List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
