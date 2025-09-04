package cn.nolaurene.cms.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private String code;

    /**
     * 描述
     */
    private String message;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(int errorCode, String message) {
        super(message);
        this.code = String.valueOf(errorCode);
        this.message = message;
    }

    public BusinessException(String message) {
        super(message);
        this.code = "BIZ_ERROR";
        this.message = message;
    }
}
