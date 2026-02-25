package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.sandbox.backend.llm.ChatMemory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

import static cn.nolaurene.cms.service.sandbox.backend.utils.PromptRenderer.*;

@Slf4j
public class Executor {

    public String conclude(ChatModel chatModel, List<cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage> memory) throws IOException {
        List<cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage> filtered = removeSystemPrompt(memory);
        List<ChatMessage> messages = new ArrayList<>();
        for (cn.nolaurene.cms.common.sandbox.backend.llm.ChatMessage msg : filtered) {
            messages.add(msg.toLangchain4j());
        }

        String conclusionPrompt = loadPrompt("prompts/conclusion.jinja");
        messages.add(UserMessage.from(conclusionPrompt));

        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
        return response.aiMessage().text();
    }
}
