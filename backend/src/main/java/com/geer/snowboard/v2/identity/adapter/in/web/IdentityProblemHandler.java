package com.geer.snowboard.v2.identity.adapter.in.web;

import com.geer.snowboard.v2.identity.application.service.IdentityService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class IdentityProblemHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalid() { return problem(HttpStatus.BAD_REQUEST, "Invalid request", "请检查输入内容"); }

    @ExceptionHandler(IdentityService.RateLimited.class)
    public ResponseEntity<Map<String, Object>> limited() { return problem(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", "操作太频繁，请稍后再试"); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException exception) {
        return problem(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getStatusCode().toString(),
                exception.getReason() == null ? "请求未完成" : exception.getReason());
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("status", status.value(), "title", title, "detail", detail));
    }
}
