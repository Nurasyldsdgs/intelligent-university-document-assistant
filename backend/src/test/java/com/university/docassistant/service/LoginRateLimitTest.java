package com.university.docassistant.service;

import com.university.docassistant.dto.AuthDtos.AuthResponse;
import com.university.docassistant.dto.AuthDtos.LoginRequest;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.exception.ErrorCodes;
import com.university.docassistant.repository.UserRepository;
import com.university.docassistant.security.ClientIpResolver;
import com.university.docassistant.security.JwtTokenProvider;
import com.university.docassistant.security.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Password brute force: (email, IP) pairs and IPs are throttled; an email alone is never locked. */
class LoginRateLimitTest {

    private static final String EMAIL = "victim@uni.kz";
    private static final String GOOD = "correct-password";
    private static final String ATTACKER_IP = "203.0.113.7";
    private static final String OFFICE_IP = "198.51.100.20";

    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    private final AuditService audit = mock(AuditService.class);
    private final MutableClock clock = new MutableClock();
    private final AuthService auth = new AuthService(users, mock(PasswordEncoder.class), authManager, jwt,
            mock(EmailService.class), 10, 60, 5, new LoginAttemptLimiter(clock), audit);

    @BeforeEach
    void setUp() {
        User victim = User.builder().id(1L).email(EMAIL).password("hash").fullName("Жертва")
                .role(Role.ROLE_STUDENT).emailVerified(true).build();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(victim));
        when(users.findByEmail("nobody@uni.kz")).thenReturn(Optional.empty());
        when(jwt.generateToken(anyString(), any())).thenReturn("jwt");
        when(authManager.authenticate(any())).thenAnswer(invocation -> {
            UsernamePasswordAuthenticationToken token = invocation.getArgument(0);
            if (!GOOD.equals(token.getCredentials())) {
                throw new BadCredentialsException("bad");
            }
            return token;
        });
    }

    @Test
    void fiveFailuresBlockThePairAndOnlyTheLockoutIsAudited() {
        for (int attempt = 1; attempt < LoginAttemptLimiter.PAIR_MAX_FAILURES; attempt++) {
            String password = "guess" + attempt;
            assertStatus(() -> login(EMAIL, password, ATTACKER_IP), HttpStatus.UNAUTHORIZED);
        }
        ApiException lockout = catchThrowableOfType(() -> login(EMAIL, "guess5", ATTACKER_IP), ApiException.class);

        assertThat(lockout.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(lockout.getCode()).isEqualTo(ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);
        assertThat(lockout.getMessage()).isEqualTo("Слишком много неудачных попыток входа. Попробуйте снова через 15 мин.");
        assertThat(lockout.getRetryAfterSeconds()).isEqualTo(15 * 60);
        verify(audit, times(1)).record(eq(EMAIL), eq(AuditService.LOGIN_LOCKED), contains(ATTACKER_IP));
    }

    @Test
    void theCorrectPasswordIsRejectedWhileBlockedWithoutBeingChecked() {
        lockPair(EMAIL, ATTACKER_IP);
        clock.advance(Duration.ofMinutes(14));

        ApiException blocked = catchThrowableOfType(() -> login(EMAIL, GOOD, ATTACKER_IP), ApiException.class);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getMessage()).endsWith("через 1 мин.");
        // The block is checked first: the password is not even verified, so the attacker learns nothing.
        verify(authManager, times(LoginAttemptLimiter.PAIR_MAX_FAILURES)).authenticate(any());
    }

    @Test
    void theRealUserOnAnotherIpIsNotLockedOut() {
        lockPair(EMAIL, ATTACKER_IP);

        AuthResponse response = login(EMAIL, GOOD, OFFICE_IP);

        assertThat(response.token()).isEqualTo("jwt");
    }

    @Test
    void successResetsThePairsCounter() {
        for (int attempt = 1; attempt < LoginAttemptLimiter.PAIR_MAX_FAILURES; attempt++) {
            String password = "typo" + attempt;
            assertStatus(() -> login(EMAIL, password, OFFICE_IP), HttpStatus.UNAUTHORIZED);
        }
        login(EMAIL, GOOD, OFFICE_IP);

        // Another 4 typos after the success: still not blocked, the earlier ones were forgotten.
        for (int attempt = 1; attempt < LoginAttemptLimiter.PAIR_MAX_FAILURES; attempt++) {
            String password = "typo" + attempt;
            assertStatus(() -> login(EMAIL, password, OFFICE_IP), HttpStatus.UNAUTHORIZED);
        }
        assertThat(login(EMAIL, GOOD, OFFICE_IP).token()).isEqualTo("jwt");
        verify(audit, never()).record(anyString(), eq(AuditService.LOGIN_LOCKED), anyString());
    }

    @Test
    void theBlockExpiresAfterFifteenMinutes() {
        lockPair(EMAIL, ATTACKER_IP);

        clock.advance(LoginAttemptLimiter.BLOCK.plusSeconds(1));

        assertThat(login(EMAIL, GOOD, ATTACKER_IP).token()).isEqualTo("jwt");
    }

    @Test
    void failuresOlderThanTheWindowDoNotCount() {
        for (int attempt = 1; attempt < LoginAttemptLimiter.PAIR_MAX_FAILURES; attempt++) {
            String password = "old" + attempt;
            assertStatus(() -> login(EMAIL, password, OFFICE_IP), HttpStatus.UNAUTHORIZED);
        }
        clock.advance(LoginAttemptLimiter.WINDOW.plusSeconds(1));

        assertStatus(() -> login(EMAIL, "new", OFFICE_IP), HttpStatus.UNAUTHORIZED); // 1 fresh failure, not the 5th.
    }

    @Test
    void anIpTryingManyEmailsIsBlockedButOtherIpsAreNot() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        for (int attempt = 1; attempt < LoginAttemptLimiter.IP_MAX_FAILURES; attempt++) {
            String email = "user" + attempt + "@uni.kz"; // Never 5 times the same email: only the IP limit applies.
            assertStatus(() -> login(email, "x", ATTACKER_IP), HttpStatus.UNAUTHORIZED);
        }
        assertStatus(() -> login("user30@uni.kz", "x", ATTACKER_IP), HttpStatus.TOO_MANY_REQUESTS);
        assertStatus(() -> login("fresh@uni.kz", "x", ATTACKER_IP), HttpStatus.TOO_MANY_REQUESTS);
        verify(audit).record(eq("user30@uni.kz"), eq(AuditService.LOGIN_LOCKED), contains("IP " + ATTACKER_IP));

        assertStatus(() -> login("fresh@uni.kz", "x", OFFICE_IP), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() {
        ApiException unknown = catchThrowableOfType(() -> login("nobody@uni.kz", "x", OFFICE_IP), ApiException.class);
        ApiException wrong = catchThrowableOfType(() -> login(EMAIL, "x", "192.0.2.1"), ApiException.class);

        assertThat(unknown.getStatus()).isEqualTo(wrong.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
        assertThat(unknown.getCode()).isEqualTo(wrong.getCode());
    }

    @Test
    void forwardedForIsIgnoredUnlessTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");

        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("10.0.0.5");
        // Behind a trusted proxy: the right-most hop is the one the proxy added; the left ones are client-supplied.
        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("203.0.113.9");
        assertThat(new ClientIpResolver(true).resolve(new MockHttpServletRequest())).isEqualTo("127.0.0.1");
    }

    private void lockPair(String email, String ip) {
        for (int attempt = 1; attempt <= LoginAttemptLimiter.PAIR_MAX_FAILURES; attempt++) {
            try {
                login(email, "guess" + attempt, ip);
            } catch (ApiException expected) {
                // 401 four times, then 429.
            }
        }
        assertStatus(() -> login(email, "anything", ip), HttpStatus.TOO_MANY_REQUESTS);
    }

    private AuthResponse login(String email, String password, String ip) {
        return auth.login(new LoginRequest(email, password), ip);
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }

    /** A clock the test moves forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-12T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
