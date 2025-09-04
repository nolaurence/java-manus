package cn.nolaurene.cms.common.sandbox.backend.model.message;

import lombok.Getter;

public enum AssistantMessageType {
    SHELL("shell"),
    FILE("file"),
    BROWSER("browser"),
    INFO("info"),
    MESSAGE("message");

    @Getter
    private final String message;

    AssistantMessageType(String message) {
        this.message = message;
    }
}
