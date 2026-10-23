package ru.quietclinic.platform;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(DomainException.class)
  org.springframework.http.ResponseEntity<ApiError> domain(DomainException ex) {
    return org.springframework.http.ResponseEntity.status(ex.status)
        .body(new ApiError(Instant.now(), ex.status.value(), ex.code, ex.getMessage(), Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  org.springframework.http.ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
    var fields = new java.util.LinkedHashMap<String, String>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
    return org.springframework.http.ResponseEntity.badRequest()
        .body(
            new ApiError(
                Instant.now(), 400, "VALIDATION_ERROR", "Check the request fields", fields));
  }
}
