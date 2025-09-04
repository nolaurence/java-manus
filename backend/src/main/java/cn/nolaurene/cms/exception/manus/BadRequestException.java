package cn.nolaurene.cms.exception.manus;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description:
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
