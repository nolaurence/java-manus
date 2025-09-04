package cn.nolaurene.cms.common.sandbox.backend.model.data;

import lombok.Getter;

public enum StepEventStatus {
    pending("pending"),
    running("running"),
    completed("completed"),
    failed("failed");

    @Getter
    private final String code;

    StepEventStatus(String code) {
        this.code = code;
    }

}
