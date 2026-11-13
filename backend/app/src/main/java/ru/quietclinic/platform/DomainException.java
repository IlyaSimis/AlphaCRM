package ru.quietclinic.platform;

import org.springframework.http.HttpStatus;

/**
 * Ошибка предметной области. {@code code} — машинный код на английском: по нему интерфейс выбирает
 * фразу для сотрудника. {@code message} — английское пояснение для разработчика и для экранов,
 * которые этот код ещё не знают.
 */
class DomainException extends RuntimeException {
  final String code;
  final HttpStatus status;

  DomainException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  static DomainException conflict(String code, String message) {
    return new DomainException(code, message, HttpStatus.CONFLICT);
  }

  static DomainException badRequest(String code, String message) {
    return new DomainException(code, message, HttpStatus.BAD_REQUEST);
  }

  static DomainException forbidden(String code, String message) {
    return new DomainException(code, message, HttpStatus.FORBIDDEN);
  }

  static DomainException unauthorized(String message) {
    return unauthorized("UNAUTHORIZED", message);
  }

  static DomainException unauthorized(String code, String message) {
    return new DomainException(code, message, HttpStatus.UNAUTHORIZED);
  }

  static DomainException tooManyRequests(String code, String message) {
    return new DomainException(code, message, HttpStatus.TOO_MANY_REQUESTS);
  }

  static DomainException payloadTooLarge(String code, String message) {
    return new DomainException(code, message, HttpStatus.PAYLOAD_TOO_LARGE);
  }

  static DomainException notFound(String code, String message) {
    return new DomainException(code, message, HttpStatus.NOT_FOUND);
  }
}
