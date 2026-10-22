package ru.quietclinic.platform;

import org.springframework.http.HttpStatus;

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

  static DomainException forbidden(String message) {
    return new DomainException("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
  }

  static DomainException notFound(String message) {
    return new DomainException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }
}
