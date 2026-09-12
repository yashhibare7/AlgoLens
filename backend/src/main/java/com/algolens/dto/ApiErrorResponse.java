package com.algolens.dto;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape every endpoint returns, so the frontend has exactly one thing to parse.
 *
 * @param timestamp   when the failure happened
 * @param status      HTTP status code
 * @param error       short machine-friendly code, e.g. VALIDATION_FAILED
 * @param message     human readable explanation, safe to show to the user
 * @param path        the request path
 * @param fieldErrors per-field messages for validation failures, otherwise null
 * @param details     extra machine-readable context (credits required/available, ...)
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors,
        Map<String, Object> details) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null, null);
    }
}
