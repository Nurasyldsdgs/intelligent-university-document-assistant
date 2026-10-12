package com.university.docassistant.exception;

/**
 * Machine-readable error codes returned in {@link ErrorResponse#code()}.
 */
public final class ErrorCodes {

    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String EMAIL_ALREADY_VERIFIED = "EMAIL_ALREADY_VERIFIED";
    public static final String OTP_RESEND_COOLDOWN = "OTP_RESEND_COOLDOWN";
    /** Registration with an email that already has an account; the form highlights the email field. */
    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    /** Wrong email or password at login; deliberately the same for both, see AuthService.login. */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    /** Some fields failed validation; {@link ErrorResponse#fieldErrors()} says which. */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    private ErrorCodes() {
    }
}
