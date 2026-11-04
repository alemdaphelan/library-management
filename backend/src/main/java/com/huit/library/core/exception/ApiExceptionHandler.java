package com.huit.library.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(exception.getStatusCode());
        String reason = exception.getReason();
        if (reason != null && !reason.isBlank()) {
            problem.setDetail(reason);
        }
        return ResponseEntity.status(exception.getStatusCode()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse("Dữ liệu gửi lên chưa hợp lệ. Vui lòng kiểm tra lại.");
        problem.setDetail(detail);
        return ResponseEntity.badRequest().body(problem);
    }
}
