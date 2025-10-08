package cn.nolaurene.cms.common.sandbox.backend.model;

import lombok.Getter;

@Getter
public enum SSEEventType {
    TOOL("tool"),
    STEP("step"),
    MESSAGE("message"),
    ERROR("error"),
    DONE("DONE"),
    TITLE("title"),
    PLAN("plan");

    private final String type;

    SSEEventType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static SSEEventType fromType(String type) {
        for (SSEEventType value : values()) {
            if (value.getType().equals(type)) {
                return value;
            }
        }
        return null;
    }
}
