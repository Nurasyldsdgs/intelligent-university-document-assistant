package com.university.docassistant.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * Business error that maps directly to an HTTP status, a stable machine-readable {@code code} from
 * {@link ErrorCodes} and a client-facing message. There is no way to create one without a code: clients branch and
 * translate by code, never by message text.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String message, String code, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = Objects.requireNonNull(code, "ApiException needs an ErrorCodes code");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, message, code, null);
    }

    public static ApiException badRequest(String code, String message) {
        return of(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return of(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return of(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return of(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return of(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException tooManyRequests(String message, long retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message, ErrorCodes.OTP_RESEND_COOLDOWN, retryAfterSeconds);
    }
}
