package com.empresa.sistema.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private ResponseEntity<Map<String, Object>> error(
      HttpStatus status, String code, String message, HttpServletRequest r, Object details) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now());
    body.put("status", status.value());
    body.put("code", code);
    body.put("message", message);
    body.put("path", r.getRequestURI());
    body.put("details", details);
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e, HttpServletRequest r) {
    return error(e.getStatus(), e.getCode(), e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dados inválidos", r, fields);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> bad(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Verifique os campos e formatos da requisição",
        r,
        Map.of());
  }

  @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
  ResponseEntity<?> header(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Cabeçalho obrigatório ausente", r, Map.of());
  }

  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  ResponseEntity<?> parameter(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Parâmetro obrigatório ausente", r, Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> denied(Exception e, HttpServletRequest r) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado", r, Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> integrity(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.CONFLICT,
        "CONSTRAINT_CONFLICT",
        "Identificador já utilizado ou relacionamento inválido",
        r,
        Map.of());
  }

  @ExceptionHandler({
    PessimisticLockingFailureException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<?> locking(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.CONFLICT,
        "RETRY_TRANSACTION",
        "Conflito concorrente; repita a requisição com o mesmo eventId",
        r,
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> internal(Exception e, HttpServletRequest r) {
    log.error("Request failed: {}", r.getRequestURI(), e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno", r, Map.of());
  }
}
