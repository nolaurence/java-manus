package cn.nolaurene.cms.common.enums.sandbox;

import lombok.Getter;

@Getter
public enum ConversationStatus {
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED");

    private final String code;

    ConversationStatus(String code) {
        this.code = code;
    }
}
