package cn.nolaurene.cms.exception.manus;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description:
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
