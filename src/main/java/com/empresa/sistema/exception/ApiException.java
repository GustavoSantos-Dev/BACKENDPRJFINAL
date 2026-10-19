package com.empresa.sistema.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException missing(String resource) {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " não encontrado");
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
