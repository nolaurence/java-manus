package cn.nolaurene.cms.common.sandbox.backend.model;

import lombok.Getter;

@Getter
public enum SSEEventType {
    UNKNOWN("unknown"),
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

    public static SSEEventType fromType(String type) {
        for (SSEEventType value : values()) {
            if (value.getType().equals(type)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
