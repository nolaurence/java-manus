package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import lombok.Getter;

@Getter
public enum ToolType {
    READ_ONLY("readOnly"),
    DESTRUCTIVE("destructive");

    private final String value;

    ToolType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static ToolType fromString(String value) {
        for (ToolType type : ToolType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown tool type: " + value);
    }
}
