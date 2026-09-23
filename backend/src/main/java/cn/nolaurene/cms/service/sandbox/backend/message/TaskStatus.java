package cn.nolaurene.cms.service.sandbox.backend.message;

public enum TaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    LIMIT_REACHED("limit_reached"),
    FAILED("failed");

    private final String status;

    TaskStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
