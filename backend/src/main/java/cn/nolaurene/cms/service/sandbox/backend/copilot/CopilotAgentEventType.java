package cn.nolaurene.cms.service.sandbox.backend.copilot;

/** Events emitted by the Java Copilot-style agent loop. */
public enum CopilotAgentEventType {
    USER_MESSAGE("user.message"),
    ASSISTANT_TURN_START("assistant.turn_start"),
    ASSISTANT_REASONING("assistant.reasoning"),
    ASSISTANT_MESSAGE_DELTA("assistant.message_delta"),
    ASSISTANT_MESSAGE("assistant.message"),
    TOOL_EXECUTION_START("tool.execution_start"),
    TOOL_PERMISSION_REQUEST("permission.requested"),
    TOOL_EXECUTION_COMPLETE("tool.execution_complete"),
    ASSISTANT_TURN_END("assistant.turn_end"),
    SESSION_IDLE("session.idle"),
    SESSION_ERROR("session.error"),
    SESSION_LIMIT("session.limit");

    private final String wireName;

    CopilotAgentEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public String getWireName() {
        return wireName;
    }
}
