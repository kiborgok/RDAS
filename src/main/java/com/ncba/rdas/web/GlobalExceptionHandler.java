package com.ncba.rdas.web;

import com.ncba.rdas.exception.CountryNotFoundException;
import com.ncba.rdas.exception.ReferenceDataUnavailableException;
import com.ncba.rdas.soap.SoapClientException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Central translation of every exception into a consistent {@link ApiError} body
 * with the right HTTP status. Keeping this in one place guarantees uniform error
 * responses across all channels — one of the original problems with each channel
 * calling SOAP directly.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 404 -----------------------------------------------------------------

    @ExceptionHandler(CountryNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(CountryNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, List.of());
    }

    // ---- 400 (validation / bad input) ----------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        List<String> details = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            details.add(v.getPropertyPath() + ": " + v.getMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Parameter '" + ex.getName() + "' has an invalid value '" + ex.getValue() + "'";
        return build(HttpStatus.BAD_REQUEST, msg, req, List.of());
    }

    // ---- 503 (upstream unavailable) ------------------------------------------

    @ExceptionHandler(ReferenceDataUnavailableException.class)
    public ResponseEntity<ApiError> handleNoData(
            ReferenceDataUnavailableException ex, HttpServletRequest req) {
        return buildWithRetryAfter(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitOpen(
            CallNotPermittedException ex, HttpServletRequest req) {
        log.warn("Circuit breaker open: {}", ex.getMessage());
        return buildWithRetryAfter(HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream reference-data service is temporarily unavailable. Please retry shortly.", req);
    }

    @ExceptionHandler(SoapClientException.class)
    public ResponseEntity<ApiError> handleSoap(SoapClientException ex, HttpServletRequest req) {
        log.warn("Upstream SOAP error: {}", ex.getMessage());
        return buildWithRetryAfter(HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream reference-data service error. Please retry shortly.", req);
    }

    // ---- 500 -----------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error handling {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", req, List.of());
    }

    // ---- helpers -------------------------------------------------------------

    private ResponseEntity<ApiError> build(
            HttpStatus status, String message, HttpServletRequest req, List<String> details) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ApiError> buildWithRetryAfter(
            HttpStatus status, String message, HttpServletRequest req) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "30");
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}
