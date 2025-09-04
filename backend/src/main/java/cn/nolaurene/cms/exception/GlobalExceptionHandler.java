package cn.nolaurene.cms.exception;

import cn.nolaurene.cms.common.enums.ErrorShowType;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.exception.manus.AppException;
import cn.nolaurene.cms.exception.manus.BadRequestException;
import cn.nolaurene.cms.exception.manus.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseWebResult<?> businessExceptionHandler(BusinessException e) {
        log.error("businessException: " + e.getMessage(), e);
        BaseWebResult result = new BaseWebResult(false, null, e.getCode(), e.getMessage(), ErrorShowType.NOTIFICATION);
        return result;
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseWebResult<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("runtimeException", e);
        return BaseWebResult.fail(e.getMessage(), ErrorShowType.NOTIFICATION);
    }

    /**
     * Manus Sandbox
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<String> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<String> handleAppException(AppException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
