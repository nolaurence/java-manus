package cn.nolaurene.cms.common.enums;

import cn.nolaurene.cms.common.constants.UserConstants;
import lombok.Getter;

@Getter
public enum LoginErrorEnum {
    SUCCESS("0", "ok"),
    ACCOUNT_EMPTY("40001", "账号不能为空"),
    ACCOUNT_TOO_SHORT("40002", "账号长度不能小于" + UserConstants.MIN_USER_ACCOUNT_LENGTH),
    PASSWORD_TOO_SHORT("40003", "密码长度不能小于" + UserConstants.MIN_PASSWORD_LENGTH),
    ACCOUNT_BAD_CHARACTER("40004", "账号不能包含特殊字符"),
    USER_NOT_EXIST("40005", "用户不存在"),
    PASSWORD_ERROR("23333", "用户名或密码错误");

    private String errorCode;

    private String errorMessage;

    LoginErrorEnum(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
