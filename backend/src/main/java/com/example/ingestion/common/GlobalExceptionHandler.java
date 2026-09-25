package com.example.ingestion.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException error) {
        // ApiException 的 message 由代码显式书写, 可以安全回显; 这里只兜底 null
        String message = error.getMessage() == null ? "请求处理失败" : error.getMessage();
        String code = error.getCode() == null ? "" : error.getCode();
        return ResponseEntity.status(error.getStatus()).body(Map.of("error", message, "code", code));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception error) {
        // 原始 message 可能包含请求体片段与内部类型名, 只回显异常类别
        log.warn("Rejected invalid request: {}", error.toString());
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "请求参数不合法", "detail", error.getClass().getSimpleName()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "请求的资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotAllowed(HttpRequestMethodNotSupportedException error) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "请求方式不支持"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException error) {
        log.warn("Data constraint rejected request: {}", error.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "数据与现有记录冲突，请检查名称、编码或关联关系"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception error) {
        // 原始异常可能带出 SQL、连接串、文件路径等内部细节, 一律脱敏, 只回事件号供排查
        String incident = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled request error [incident={}]", incident, error);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "系统内部错误，请联系管理员并提供事件号 " + incident,
                "incident", incident));
    }
}
