package cn.nolaurene.cms.common.enums;

import lombok.Getter;

@Getter
public enum ErrorShowType {
    SILENT(0, "不提示"),
    WARN_MESSAGE(1, "警告信息"),
    ERROR_MESSAGE(2, "错误信息"),
    NOTIFICATION(4, "通知"),
    REDIRECT(9, "重定向");

    private final int code;

    private final String desc;

    ErrorShowType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
