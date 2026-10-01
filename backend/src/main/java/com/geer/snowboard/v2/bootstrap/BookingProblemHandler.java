package com.geer.snowboard.v2.bootstrap;

import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class BookingProblemHandler {
    @ExceptionHandler(BusinessProblem.class)
    public ResponseEntity<Map<String, Object>> handle(BusinessProblem problem) {
        return ResponseEntity.status(problem.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("status", problem.status(), "title", "Request failed", "detail", problem.getMessage()));
    }
}
