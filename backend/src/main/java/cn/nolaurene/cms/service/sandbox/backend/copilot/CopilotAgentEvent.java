package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small event envelope matching the stable Copilot event semantics: type,
 * timestamp, turn/tool correlation and structured data.
 */
public final class CopilotAgentEvent {

    private final CopilotAgentEventType type;
    private final Instant timestamp;
    private final int turn;
    private final String toolCallId;
    private final String toolName;
    private final String content;
    private final Map<String, Object> arguments;
    private final Object result;
    private final String error;
    private final Map<String, Object> data;

    private CopilotAgentEvent(Builder builder) {
        this.type = builder.type;
        this.timestamp = builder.timestamp == null ? Instant.now() : builder.timestamp;
        this.turn = builder.turn;
        this.toolCallId = builder.toolCallId;
        this.toolName = builder.toolName;
        this.content = builder.content;
        this.arguments = immutableMap(builder.arguments);
        this.result = builder.result;
        this.error = builder.error;
        this.data = immutableMap(builder.data);
    }

    public CopilotAgentEventType type() { return type; }
    public CopilotAgentEventType getType() { return type; }
    public Instant timestamp() { return timestamp; }
    public Instant getTimestamp() { return timestamp; }
    public int turn() { return turn; }
    public int getTurn() { return turn; }
    public String toolCallId() { return toolCallId; }
    public String getToolCallId() { return toolCallId; }
    public String toolName() { return toolName; }
    public String getToolName() { return toolName; }
    public String content() { return content; }
    public String getContent() { return content; }
    public Map<String, Object> arguments() { return arguments; }
    public Map<String, Object> getArguments() { return arguments; }
    public Object result() { return result; }
    public Object getResult() { return result; }
    public String error() { return error; }
    public String getError() { return error; }
    public Map<String, Object> data() { return data; }
    public Map<String, Object> getData() { return data; }

    /** Serialize the event using the names used by Copilot-style consumers. */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type == null ? null : type.wireName());
        result.put("timestamp", timestamp.toString());
        if (turn > 0) result.put("turn", turn);
        if (toolCallId != null) result.put("toolCallId", toolCallId);
        if (toolName != null) result.put("toolName", toolName);
        if (content != null) result.put("content", content);
        if (!arguments.isEmpty()) result.put("arguments", arguments);
        if (this.result != null) result.put("result", this.result);
        if (error != null) result.put("error", error);
        Map<String, Object> wireData = new LinkedHashMap<>(data);
        if (content != null) {
            if (type == CopilotAgentEventType.ASSISTANT_MESSAGE_DELTA) {
                wireData.putIfAbsent("deltaContent", content);
            } else {
                wireData.putIfAbsent("content", content);
            }
        }
        if (toolCallId != null) wireData.putIfAbsent("toolCallId", toolCallId);
        if (toolName != null) wireData.putIfAbsent("toolName", toolName);
        if (arguments != null && !arguments.isEmpty()) {
            wireData.putIfAbsent("arguments", arguments);
        }
        if (this.result != null) wireData.putIfAbsent("result", this.result);
        if (error != null) wireData.putIfAbsent("error", error);
        if (!wireData.isEmpty()) result.put("data", Collections.unmodifiableMap(wireData));
        return Collections.unmodifiableMap(result);
    }

    public static Builder builder(CopilotAgentEventType type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final CopilotAgentEventType type;
        private Instant timestamp;
        private int turn;
        private String toolCallId;
        private String toolName;
        private String content;
        private Map<String, Object> arguments;
        private Object result;
        private String error;
        private Map<String, Object> data;

        private Builder(CopilotAgentEventType type) {
            this.type = type;
        }

        public Builder timestamp(Instant value) { this.timestamp = value; return this; }
        public Builder turn(int value) { this.turn = value; return this; }
        public Builder toolCallId(String value) { this.toolCallId = value; return this; }
        public Builder toolName(String value) { this.toolName = value; return this; }
        public Builder content(String value) { this.content = value; return this; }
        public Builder arguments(Map<String, Object> value) { this.arguments = value; return this; }
        public Builder result(Object value) { this.result = value; return this; }
        public Builder error(String value) { this.error = value; return this; }
        public Builder data(Map<String, Object> value) { this.data = value; return this; }
        public CopilotAgentEvent build() { return new CopilotAgentEvent(this); }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
