package cn.nolaurene.cms.common.sandbox.backend.llm;


import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
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
        List<ChatMessage> copy = new ArrayList<>();
        for (ChatMessage message : history) {
            ChatMessage cloned = new ChatMessage(message.getRole(), message.getEventType(), message.getContent());
            copy.add(cloned);
        }
        return copy;
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
     * 估算规则：
     * - 中文字符：每个字符约1.5个token
     * - 英文单词：平均每个单词约1.3个token
     * - 数字和标点：每个字符约0.5个token
     * 
     * @return 估算的token总数
     */
    public int calculateTokenCount() {
        int totalTokens = 0;
        for (ChatMessage message : history) {
            if (message.getContent() != null) {
                totalTokens += estimateTokens(message.getContent());
            }
            // 每条消息额外增加约4个token（用于role、metadata等）
            totalTokens += 4;
        }
        return totalTokens;
    }

    /**
     * 估算单个文本的token数量
     * 
     * @param text 要估算的文本
     * @return 估算的token数
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int tokens = 0;
        int chineseCount = 0;
        int englishWordCount = 0;
        int otherCharCount = 0;

        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // 判断是否为中文字符（CJK统一汉字）
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseCount++;
                inWord = false;
            }
            // 判断是否为英文字母
            else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                if (!inWord) {
                    englishWordCount++;
                    inWord = true;
                }
            }
            // 空格、换行等分隔符
            else if (Character.isWhitespace(c)) {
                inWord = false;
                otherCharCount++;
            }
            // 其他字符（数字、标点等）
            else {
                otherCharCount++;
                inWord = false;
            }
        }

        // 计算token：中文1.5，英文单词1.3，其他0.5
        tokens = (int) Math.ceil(chineseCount * 1.5 + englishWordCount * 1.3 + otherCharCount * 0.5);
        
        return tokens;
    }
}
