package cn.nolaurene.cms.service.sandbox.backend.copilot;

/** Deferral hint for lazy tool catalogs. */
public enum CopilotToolDefer {
    AUTO("auto"),
    NEVER("never");

    private final String wireValue;

    CopilotToolDefer(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static CopilotToolDefer fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CopilotToolDefer defer : values()) {
            if (defer.wireValue.equalsIgnoreCase(value)) {
                return defer;
            }
        }
        throw new IllegalArgumentException("Unknown tool defer mode: " + value);
    }
}
