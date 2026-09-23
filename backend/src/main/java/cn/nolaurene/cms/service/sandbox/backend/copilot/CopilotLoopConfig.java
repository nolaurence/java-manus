package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Limits and policies for one in-process agent run. */
public final class CopilotLoopConfig {

    private int maxTurns = 30;
    private long modelTimeoutMillis = 300_000L;
    private long toolTimeoutMillis = 300_000L;
    private int maxContextMessages = 200;
    private int maxTranscriptMessages = 1_000;
    // ASK is the protocol default. A host may install an async request handler
    // for interactive approval, while the web executor installs its explicit
    // allow/deny policy.
    private CopilotPermissionHandler permissionHandler = (invocation, definition) ->
            CopilotPermissionDecision.ASK;
    private CopilotPermissionRequestHandler permissionRequestHandler;
    private Consumer<CopilotPermissionRequest> permissionRequestSink = ignored -> { };
    /** Host metadata made available to every tool invocation in this run. */
    private Map<String, Object> invocationContext = Collections.emptyMap();

    public int getMaxTurns() { return maxTurns; }
    public CopilotLoopConfig setMaxTurns(int value) {
        if (value < 1) throw new IllegalArgumentException("maxTurns must be positive");
        this.maxTurns = value;
        return this;
    }

    /** Maximum time spent in one synchronous chat-model request. */
    public long getModelTimeoutMillis() { return modelTimeoutMillis; }
    public CopilotLoopConfig setModelTimeoutMillis(long value) {
        if (value < 1) throw new IllegalArgumentException("modelTimeoutMillis must be positive");
        this.modelTimeoutMillis = value;
        return this;
    }

    public long getToolTimeoutMillis() { return toolTimeoutMillis; }
    public CopilotLoopConfig setToolTimeoutMillis(long value) {
        if (value < 1) throw new IllegalArgumentException("toolTimeoutMillis must be positive");
        this.toolTimeoutMillis = value;
        return this;
    }

    public int getMaxContextMessages() { return maxContextMessages; }
    public CopilotLoopConfig setMaxContextMessages(int value) {
        if (value < 4) throw new IllegalArgumentException("maxContextMessages must be at least 4");
        this.maxContextMessages = value;
        return this;
    }

    /** Bound the canonical restart transcript independently from request context. */
    public int getMaxTranscriptMessages() { return maxTranscriptMessages; }
    public CopilotLoopConfig setMaxTranscriptMessages(int value) {
        if (value < 4) throw new IllegalArgumentException("maxTranscriptMessages must be at least 4");
        this.maxTranscriptMessages = value;
        return this;
    }

    public CopilotPermissionHandler getPermissionHandler() { return permissionHandler; }
    public CopilotLoopConfig setPermissionHandler(CopilotPermissionHandler value) {
        this.permissionHandler = value == null
                ? (invocation, definition) -> CopilotPermissionDecision.ASK : value;
        return this;
    }

    /** Optional async approval hook used when the synchronous policy returns ASK. */
    public CopilotPermissionRequestHandler getPermissionRequestHandler() {
        return permissionRequestHandler;
    }

    public CopilotLoopConfig setPermissionRequestHandler(CopilotPermissionRequestHandler value) {
        this.permissionRequestHandler = value;
        return this;
    }

    public Consumer<CopilotPermissionRequest> getPermissionRequestSink() {
        return permissionRequestSink;
    }

    public CopilotLoopConfig setPermissionRequestSink(Consumer<CopilotPermissionRequest> value) {
        this.permissionRequestSink = value == null ? ignored -> { } : value;
        return this;
    }

    public Map<String, Object> getInvocationContext() {
        return invocationContext;
    }

    public CopilotLoopConfig setInvocationContext(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            this.invocationContext = Collections.emptyMap();
        } else {
            this.invocationContext = Collections.unmodifiableMap(new LinkedHashMap<>(value));
        }
        return this;
    }
}
