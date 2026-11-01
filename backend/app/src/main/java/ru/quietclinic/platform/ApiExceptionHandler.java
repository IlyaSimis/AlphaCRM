package ru.quietclinic.platform;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(DomainException.class)
  ResponseEntity<ApiError> domain(DomainException ex) {
    return ResponseEntity.status(ex.status)
        .body(new ApiError(Instant.now(), ex.status.value(), ex.code, ex.getMessage(), Map.of()));
  }

  /** Файл больше лимита multipart: тот же ответ, что и проверка размера в сервисе. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(413)
        .body(
            new ApiError(Instant.now(), 413, "FILE_TOO_LARGE", "The file is too large", Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                Instant.now(), 400, "VALIDATION_ERROR", "Check the request fields", fields));
  }
}
