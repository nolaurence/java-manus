package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * A permission decision that may be completed by an interactive host while a
 * tool call is waiting in the agent loop.
 */
public final class CopilotPermissionRequest {

    private final String requestId;
    private final CopilotToolInvocation invocation;
    private final CopilotToolDefinition definition;
    private final CompletableFuture<CopilotPermissionDecision> decision = new CompletableFuture<>();

    public CopilotPermissionRequest(String requestId,
                                    CopilotToolInvocation invocation,
                                    CopilotToolDefinition definition) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public String requestId() { return requestId; }
    public String getRequestId() { return requestId; }
    public CopilotToolInvocation invocation() { return invocation; }
    public CopilotToolInvocation getInvocation() { return invocation; }
    public CopilotToolDefinition definition() { return definition; }
    public CopilotToolDefinition getDefinition() { return definition; }

    /** Future completed by {@link #resolve(CopilotPermissionDecision)}. */
    public CompletableFuture<CopilotPermissionDecision> decision() { return decision; }
    public CompletableFuture<CopilotPermissionDecision> getDecision() { return decision; }

    public boolean resolve(CopilotPermissionDecision value) {
        return decision.complete(value == null ? CopilotPermissionDecision.DENY : value);
    }

    public boolean deny() {
        return resolve(CopilotPermissionDecision.DENY);
    }

    /** Wire-shaped payload for a `permission.requested` event. */
    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("requestId", requestId);
        value.put("kind", "tool");
        value.put("toolName", definition.name());
        value.put("arguments", invocation.getArguments());
        value.put("sessionId", invocation.getSessionId());
        return Collections.unmodifiableMap(value);
    }
}
