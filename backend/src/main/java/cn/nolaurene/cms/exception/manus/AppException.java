package cn.nolaurene.cms.exception.manus;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description:
 */
public class AppException extends RuntimeException {

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
