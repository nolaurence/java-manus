package cn.nolaurene.cms.service.sandbox.backend.copilot;

public enum CopilotPermissionDecision {
    ALLOW("allow"),
    DENY("deny"),
    /** Defer to an interactive host; headless runs treat this as denied. */
    ASK("ask");

    private final String wireValue;

    CopilotPermissionDecision(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
