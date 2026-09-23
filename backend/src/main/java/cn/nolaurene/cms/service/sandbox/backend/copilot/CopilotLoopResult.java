package cn.nolaurene.cms.service.sandbox.backend.copilot;

import dev.langchain4j.data.message.ChatMessage;

import java.util.Collections;
import java.util.List;

/** Result and transcript of one Copilot-style run. */
public final class CopilotLoopResult {
    private final String finalText;
    private final List<ChatMessage> messages;
    private final int turns;
    private final boolean reachedLimit;
    private final boolean aborted;

    public CopilotLoopResult(String finalText, List<ChatMessage> messages, int turns, boolean reachedLimit) {
        this(finalText, messages, turns, reachedLimit, false);
    }

    public CopilotLoopResult(String finalText,
                             List<ChatMessage> messages,
                             int turns,
                             boolean reachedLimit,
                             boolean aborted) {
        this.finalText = finalText;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.turns = turns;
        this.reachedLimit = reachedLimit;
        this.aborted = aborted;
    }

    public String getFinalText() { return finalText; }
    public String finalText() { return finalText; }
    public List<ChatMessage> getMessages() { return Collections.unmodifiableList(messages); }
    public List<ChatMessage> messages() { return getMessages(); }
    public List<ChatMessage> transcript() { return getMessages(); }
    public int getTurns() { return turns; }
    public int turns() { return turns; }
    public boolean isReachedLimit() { return reachedLimit; }
    public boolean reachedLimit() { return reachedLimit; }
    public boolean isAborted() { return aborted; }
    public boolean aborted() { return aborted; }
}
