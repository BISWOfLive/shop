package com.chengli.notification.exception;

import com.chengli.notification.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/** 内部接口统一 JSON 错误；不转发堆栈、凭据或数据库细节。 */
@Slf4j
@RestControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? defaultMessage(resolved) : exception.getReason();
        return response(resolved, message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> binding(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.isBindingFailure()
                        ? error.getField() + " 格式不正确" : error.getDefaultMessage())
                .distinct().collect(Collectors.joining("；"));
        return response(HttpStatus.BAD_REQUEST, message.isBlank() ? "请求参数不正确" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraint(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage).distinct().collect(Collectors.joining("；"));
        return response(HttpStatus.BAD_REQUEST, message.isBlank() ? "请求参数不正确" : message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "请求参数格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        log.error("内部通知请求处理失败", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用，请稍后重试");
    }

    private String defaultMessage(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) return "内部凭据无效";
        if (status == HttpStatus.FORBIDDEN) return "没有通知访问权限";
        if (status == HttpStatus.NOT_FOUND) return "通知不存在";
        return "服务暂时不可用，请稍后重试";
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}
