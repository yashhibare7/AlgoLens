package com.algolens.exception;

import com.algolens.dto.ApiErrorResponse;
import com.algolens.execution.ExecutionSandbox;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every exception into the one {@link ApiErrorResponse} shape.
 *
 * <p>Deliberate rule: messages for 4xx are written for the user and are safe to display; the 5xx
 * handler logs the exception and returns a generic message, so stack traces and internal details
 * never reach a client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException e,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException e,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException e,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), request);
    }

    @ExceptionHandler(InsufficientCreditsException.class)
    public ResponseEntity<ApiErrorResponse> handleCredits(InsufficientCreditsException e,
            HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("required", e.required());
        details.put("available", e.available());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ApiErrorResponse(Instant.now(), HttpStatus.PAYMENT_REQUIRED.value(),
                        "INSUFFICIENT_CREDITS", e.getMessage(), request.getRequestURI(), null,
                        details));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthenticated(UnauthenticatedException e,
            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", e.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException e,
            HttpServletRequest request) {
        // Never distinguish "no such account" from "wrong password": that difference is an
        // account-enumeration oracle.
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Email or password is incorrect", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiErrorResponse(Instant.now(),
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
                "Some fields are invalid", request.getRequestURI(), fieldErrors, null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception e,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body or a parameter could not be read", request);
    }

    @ExceptionHandler(ExecutionSandbox.SandboxTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleSandboxTimeout(
            ExecutionSandbox.SandboxTimeoutException e, HttpServletRequest request) {
        return build(HttpStatus.REQUEST_TIMEOUT, "EXECUTION_TIMEOUT", e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e,
            HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again.", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), code, message,
                        request.getRequestURI()));
    }
}
