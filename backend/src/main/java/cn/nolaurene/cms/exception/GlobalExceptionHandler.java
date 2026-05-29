package cn.nolaurene.cms.exception;

import cn.nolaurene.cms.common.enums.ErrorShowType;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.exception.manus.AppException;
import cn.nolaurene.cms.exception.manus.BadRequestException;
import cn.nolaurene.cms.exception.manus.ResourceNotFoundException;
import cn.nolaurene.cms.exception.skill.SkillAlreadyExistsException;
import cn.nolaurene.cms.exception.skill.SkillDependencyException;
import cn.nolaurene.cms.exception.skill.SkillException;
import cn.nolaurene.cms.exception.skill.SkillExecutionException;
import cn.nolaurene.cms.exception.skill.SkillNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String NOT_LOGIN_MESSAGE = "未登录";

    @ExceptionHandler(BusinessException.class)
    public BaseWebResult<?> businessExceptionHandler(BusinessException e) {
        if (NOT_LOGIN_MESSAGE.equals(e.getMessage())) {
            log.error("businessException: " + e.getMessage());
            return new BaseWebResult(false, null, e.getCode(), e.getMessage(), ErrorShowType.WARN_MESSAGE);
        } else {
            log.error("businessException: " + e.getMessage(), e);
            return new BaseWebResult(false, null, e.getCode(), e.getMessage(), ErrorShowType.NOTIFICATION);
        }
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

    /**
     * Skill相关异常处理
     */
    @ExceptionHandler(SkillNotFoundException.class)
    public BaseWebResult<?> handleSkillNotFound(SkillNotFoundException ex) {
        log.warn("Skill not found: {}", ex.getMessage());
        return BaseWebResult.fail(ex.getMessage(), ErrorShowType.NOTIFICATION);
    }

    @ExceptionHandler(SkillAlreadyExistsException.class)
    public BaseWebResult<?> handleSkillAlreadyExists(SkillAlreadyExistsException ex) {
        log.warn("Skill already exists: {}", ex.getMessage());
        return BaseWebResult.fail(ex.getMessage(), ErrorShowType.NOTIFICATION);
    }

    @ExceptionHandler(SkillDependencyException.class)
    public BaseWebResult<?> handleSkillDependency(SkillDependencyException ex) {
        log.warn("Skill dependency error: {}", ex.getMessage());
        return BaseWebResult.fail(ex.getMessage(), ErrorShowType.NOTIFICATION);
    }

    @ExceptionHandler(SkillExecutionException.class)
    public BaseWebResult<?> handleSkillExecution(SkillExecutionException ex) {
        log.error("Skill execution error: {}", ex.getMessage(), ex);
        return BaseWebResult.fail(ex.getMessage(), ErrorShowType.NOTIFICATION);
    }

    @ExceptionHandler(SkillException.class)
    public BaseWebResult<?> handleSkillException(SkillException ex) {
        log.error("Skill error: {}", ex.getMessage(), ex);
        return BaseWebResult.fail(ex.getMessage(), ErrorShowType.NOTIFICATION);
    }
}
