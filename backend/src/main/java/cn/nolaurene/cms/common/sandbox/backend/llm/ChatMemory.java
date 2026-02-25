package cn.nolaurene.cms.common.sandbox.backend.llm;


import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        List<ChatMessage> copy = new ArrayList<>();
        for (ChatMessage message : history) {
            ChatMessage cloned = new ChatMessage(message.getRole(), message.getEventType(), message.getContent());
            copy.add(cloned);
        }
        return copy;
    }

    /**
     * Convert history to langchain4j message types.
     * Only converts system/user/assistant messages (skips tool event messages).
     */
    public List<dev.langchain4j.data.message.ChatMessage> toLangchain4jMessages() {
        return history.stream()
                .filter(msg -> msg.getRole() != ChatMessage.Role.tool)
                .filter(msg -> msg.getEventType() == SSEEventType.MESSAGE
                        || msg.getEventType() == SSEEventType.PLAN
                        || msg.getEventType() == SSEEventType.STEP)
                .map(ChatMessage::toLangchain4j)
                .collect(Collectors.toList());
    }

    public void compact() {
        for (ChatMessage chatMessage : history) {
            if (chatMessage.getEventType() == SSEEventType.TOOL) {
                ToolEventData toolEventData = JSON.parseObject(chatMessage.getContent(), ToolEventData.class);
                toolEventData.setResult("(removed)");
                chatMessage.setContent(JSON.toJSONString(toolEventData));
            }
        }
    }

    public boolean isEmpty() {
        return history.isEmpty();
    }

    /**
     * 计算上下文的token长度
     */
    public int calculateTokenCount() {
        int totalTokens = 0;
        for (ChatMessage message : history) {
            if (message.getContent() != null) {
                totalTokens += estimateTokens(message.getContent());
            }
            totalTokens += 4;
        }
        return totalTokens;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int englishWordCount = 0;
        int otherCharCount = 0;

        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseCount++;
                inWord = false;
            }
            else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                if (!inWord) {
                    englishWordCount++;
                    inWord = true;
                }
            }
            else if (Character.isWhitespace(c)) {
                inWord = false;
                otherCharCount++;
            }
            else {
                otherCharCount++;
                inWord = false;
            }
        }

        return (int) Math.ceil(chineseCount * 1.5 + englishWordCount * 1.3 + otherCharCount * 0.5);
    }
}
