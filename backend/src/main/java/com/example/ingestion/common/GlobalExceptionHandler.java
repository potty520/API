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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException error) {
        return ResponseEntity.status(error.getStatus()).body(Map.of("error", error.getMessage(), "code", error.getCode()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception error) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", "请求参数不合法", "detail", error.getMessage()));
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
        log.error("Unhandled request error", error);
        return ResponseEntity.internalServerError().body(Map.of("error", error.getMessage() == null ? "系统内部错误" : error.getMessage()));
    }
}
