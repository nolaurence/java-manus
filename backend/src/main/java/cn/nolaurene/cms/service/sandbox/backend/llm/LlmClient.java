package cn.nolaurene.cms.service.sandbox.backend.llm;


import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage;
import cn.nolaurene.cms.common.sandbox.backend.llm.StreamResource;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public interface LlmClient {

    String chat(List<ChatMessage> messages);

    StreamResource streamChat(List<ChatMessage> messages, List<JSONObject> tools);
}
