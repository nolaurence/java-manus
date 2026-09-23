package cn.nolaurene.cms.service.sandbox.backend.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable invocation context analogous to Copilot SDK ToolInvocation. */
public final class CopilotToolInvocation {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String sessionId;
    private final String toolCallId;
    private final String toolName;
    private final Map<String, Object> arguments;
    private final Map<String, Object> context;

    public CopilotToolInvocation(String sessionId,
                                 String toolCallId,
                                 String toolName,
                                 Map<String, Object> arguments,
                                 Map<String, Object> context) {
        this.sessionId = sessionId;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.arguments = immutableMap(arguments);
        this.context = immutableMap(context);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String toolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String toolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }

    /** Deserialize validated JSON-schema arguments into a record or POJO. */
    public <T> T getArgumentsAs(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("argument type must not be null");
        }
        try {
            return MAPPER.convertValue(arguments, type);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Cannot deserialize tool arguments as "
                    + type.getName(), error);
        }
    }

    public <T> T argumentsAs(Class<T> type) {
        return getArgumentsAs(type);
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public Map<String, Object> context() {
        return context;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
