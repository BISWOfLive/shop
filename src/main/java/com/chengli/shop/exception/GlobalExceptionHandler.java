package com.chengli.shop.exception;

import com.chengli.shop.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 当前数据库固定为 MySQL，按厂商错误码翻译，无需为异常处理额外读取连接元数据。
    private final SQLExceptionTranslator sqlExceptionTranslator = new SQLErrorCodeSQLExceptionTranslator("MySQL");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException exception) {
        return response(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> binding(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.isBindingFailure() ? error.getField() + " 格式不正确" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("；"));
        return response(HttpStatus.BAD_REQUEST, message.isBlank() ? "请求参数不正确" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraint(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining("；"));
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "请求参数格式不正确");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> authentication(AuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "没有权限执行此操作");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> lockFailure(PessimisticLockingFailureException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "请求暂时繁忙，请使用原请求号恢复或重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> dataIntegrity(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "数据不满足数据库约束");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        // 部分 MyBatis-Plus 查询直接抛出 PersistenceException，未经过 Spring DAO 异常翻译。
        // 沿原因链分类底层 JDBC 异常；此时业务事务已退出并回滚，不在事务内吞掉失败。
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                var translated = sqlExceptionTranslator.translate("处理业务请求", null, sqlException);
                if (translated instanceof PessimisticLockingFailureException lockingFailure) {
                    return lockFailure(lockingFailure);
                }
                if (translated instanceof DataIntegrityViolationException integrityViolation) {
                    return dataIntegrity(integrityViolation);
                }
            }
        }
        log.error("处理业务请求失败", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}