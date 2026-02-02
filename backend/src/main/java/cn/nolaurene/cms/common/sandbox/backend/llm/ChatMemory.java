package cn.nolaurene.cms.common.sandbox.backend.llm;


import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.ToolEventData;
import com.alibaba.fastjson.JSON;

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
}
