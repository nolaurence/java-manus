package cn.nolaurene.cms.service.sandbox.worker.mcp.server;


import lombok.Getter;

/**
 * @author nolaurence
 * @date 2025/7/8 上午10:50
 * @description:
 */
@Getter
public enum ModalStateType {
    FILE_CHOOSER("fileChooser"),
    DIALOG("dialog");

    private final String value;

    ModalStateType(String value) {
        this.value = value;
    }

//    public String getValue() {
//        return value;
//    }

    @Override
    public String toString() {
        return value;
    }

    public static ModalStateType fromString(String value) {
        for (ModalStateType type : ModalStateType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown modal state type: " + value);
    }
}
