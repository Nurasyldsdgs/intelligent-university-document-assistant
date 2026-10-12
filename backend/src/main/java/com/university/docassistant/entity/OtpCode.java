package com.university.docassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One-time code with expiry, failed-attempt counter and issue time (for resend cooldown).
 * Embedded into {@link User} for both password reset and email verification.
 */
@Embeddable
@Getter
@NoArgsConstructor
@ToString(exclude = "code")
public class OtpCode {

    private String code;

    private LocalDateTime expiry;

    private LocalDateTime issuedAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int attempts = 0;

    public void issue(String newCode, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        this.code = newCode;
        this.issuedAt = now;
        this.expiry = now.plus(ttl);
        this.attempts = 0;
    }

    public void clear() {
        this.code = null;
        this.expiry = null;
        this.issuedAt = null;
        this.attempts = 0;
    }

    public boolean isPresent() {
        return code != null && expiry != null;
    }

    public boolean isExpired() {
        return expiry == null || expiry.isBefore(LocalDateTime.now());
    }

    /** Constant-time comparison to avoid leaking the code through response timing. */
    public boolean matches(String candidate) {
        if (code == null || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                code.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    /** @return number of failed attempts including this one */
    public int registerFailedAttempt() {
        return ++attempts;
    }

    /** Seconds until a new code may be sent, or 0 if resending is allowed now. */
    public long secondsUntilResendAllowed(Duration cooldown) {
        if (issuedAt == null) {
            return 0;
        }
        long remaining = Duration.between(LocalDateTime.now(), issuedAt.plus(cooldown)).toSeconds();
        return Math.max(0, remaining);
    }
}
