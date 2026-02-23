package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import lombok.Getter;

@Getter
public enum ToolCapability {
    CORE("core"),
    TABS("tabs"),
    PDF("pdf"),
    HISTORY("history"),
    WAIT("wait"),
    FILES("files"),
    INSTALL("install"),
    TESTING("testing"),
    SHELL("shell");

    private final String value;

    ToolCapability(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ToolCapability fromString(String value) {
        for (ToolCapability capability : ToolCapability.values()) {
            if (capability.value.equals(value)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("Unknown tool capability: " + value);
    }
}
