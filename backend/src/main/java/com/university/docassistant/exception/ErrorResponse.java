package com.university.docassistant.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Long retryAfterSeconds,
        Map<String, String> fieldErrors
) {

    /** {@code code} is one of {@link ErrorCodes}; every error response carries one. */
    public static ErrorResponse of(int status, String error, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, null, null);
    }

    public static ErrorResponse of(ApiException ex, String path) {
        return new ErrorResponse(Instant.now(), ex.getStatus().value(), ex.getStatus().getReasonPhrase(),
                ex.getCode(), ex.getMessage(), path, ex.getRetryAfterSeconds(), null);
    }
}
