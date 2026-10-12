package com.university.docassistant.service;

import com.university.docassistant.dto.AuthDtos.AuthResponse;
import com.university.docassistant.dto.AuthDtos.ForgotPasswordRequest;
import com.university.docassistant.dto.AuthDtos.LoginRequest;
import com.university.docassistant.dto.AuthDtos.MessageResponse;
import com.university.docassistant.dto.AuthDtos.RegisterRequest;
import com.university.docassistant.dto.AuthDtos.ResendVerificationRequest;
import com.university.docassistant.dto.AuthDtos.ResetPasswordRequest;
import com.university.docassistant.dto.AuthDtos.VerifyEmailRequest;
import com.university.docassistant.dto.AuthDtos.VerifyOtpRequest;
import com.university.docassistant.entity.AuthProvider;
import com.university.docassistant.entity.OtpCode;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.exception.ErrorCodes;
import com.university.docassistant.repository.UserRepository;
import com.university.docassistant.security.JwtTokenProvider;
import com.university.docassistant.security.LoginAttemptLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
public class AuthService {

    private static final String INVALID_OTP = "Invalid or expired code";
    private static final String VERIFICATION_SENT = "Код подтверждения отправлен на ваш email";
    static final String TEACHER_REQUEST_PENDING = "Роль преподавателя подтвердит деканат после проверки заявки. "
            + "До этого вы входите как студент.";
    private static final String EMAIL_NOT_VERIFIED = "Пожалуйста, подтвердите ваш email перед входом";
    private static final String EMAIL_ALREADY_VERIFIED = "Email уже подтверждён. Войдите в систему.";
    private static final String EMAIL_TAKEN =
            "Пользователь с таким email уже зарегистрирован. Попробуйте войти или используйте другой email.";
    private static final String INVALID_CREDENTIALS = "Неверный email или пароль. Проверьте данные или восстановите доступ.";
    private static final String FORGOT_PASSWORD_RESPONSE =
            "If an account with this email exists, a reset code has been sent";

    private final UserRepository userRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final Duration otpTtl;
    private final Duration resendCooldown;
    private final int otpMaxAttempts;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       EmailService emailService,
                       @Value("${app.otp.expiration-minutes}") long otpExpirationMinutes,
                       @Value("${app.otp.resend-cooldown-seconds}") long resendCooldownSeconds,
                       @Value("${app.otp.max-attempts}") int otpMaxAttempts,
                       LoginAttemptLimiter loginAttemptLimiter,
                       AuditService auditService) {
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.otpTtl = Duration.ofMinutes(otpExpirationMinutes);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.otpMaxAttempts = otpMaxAttempts;
    }

    /**
     * Creates an unverified account and emails a verification code. No JWT is issued here:
     * the token is returned only by {@link #verifyEmail} once the email is confirmed.
     * Every self-registered account is ROLE_STUDENT: asking for ROLE_TEACHER only records a request that ADMIN
     * approves or rejects (AdminService); ROLE_ADMIN cannot be asked for at all.
     */
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        Role requested = request.role() != null ? request.role() : Role.ROLE_STUDENT;

        if (requested == Role.ROLE_ADMIN) {
            throw ApiException.forbidden(ErrorCodes.ADMIN_ROLE_NOT_ALLOWED, "Роль «Деканат» нельзя выбрать при самостоятельной регистрации. "
                    + "Обратитесь в деканат.");
        }
        // An unverified account is not overwritten either: that would let anyone replace the password
        // of a pending registration. Its owner finishes verification by logging in with their password.
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN, ErrorCodes.EMAIL_TAKEN, null);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(Role.ROLE_STUDENT)
                .teacherRequested(requested == Role.ROLE_TEACHER)
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();

        // If sending fails, EmailSendException rolls back the whole registration and returns 503.
        issueVerificationOtp(user);
        log.info("Registered new user {} as ROLE_STUDENT{}, awaiting email verification", email,
                user.isTeacherRequested() ? " with a teacher role request" : "");
        return new MessageResponse(user.isTeacherRequested()
                ? VERIFICATION_SENT + ". " + TEACHER_REQUEST_PENDING
                : VERIFICATION_SENT);
    }

    // noRollbackFor: failed attempts must be persisted even though we throw.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> ApiException.badRequest(ErrorCodes.OTP_INVALID, INVALID_OTP));

        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, EMAIL_ALREADY_VERIFIED, ErrorCodes.EMAIL_ALREADY_VERIFIED, null);
        }
        checkOtp(user, user.getEmailVerificationOtp(), request.otp());

        user.setEmailVerified(true);
        user.getEmailVerificationOtp().clear();
        userRepository.save(user);

        log.info("Email verified for {}", user.getEmail());
        return toAuthResponse(user, jwtTokenProvider.generateToken(user.getEmail(), user.getRole()));
    }

    @Transactional
    public MessageResponse resendVerificationOtp(ResendVerificationRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Same answer as for an existing account, to avoid confirming which emails are registered.
            log.info("Verification code requested for unknown email {}", email);
            return new MessageResponse(VERIFICATION_SENT);
        }
        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, EMAIL_ALREADY_VERIFIED, ErrorCodes.EMAIL_ALREADY_VERIFIED, null);
        }
        long wait = user.getEmailVerificationOtp().secondsUntilResendAllowed(resendCooldown);
        if (wait > 0) {
            throw ApiException.tooManyRequests(
                    "Повторно отправить код можно через " + wait + " с.", wait);
        }

        issueVerificationOtp(user);
        return new MessageResponse(VERIFICATION_SENT);
    }

    @Transactional(readOnly = true)
    /**
     * Password login from {@code clientIp}. Brute force is limited by LoginAttemptLimiter: a blocked (email, IP)
     * pair or IP gets 429 before the password is even checked, so it learns nothing; a correct password clears the
     * pair's failures.
     */
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());

        long blockedFor = loginAttemptLimiter.blockedForSeconds(email, clientIp);
        if (blockedFor > 0) {
            throw tooManyLoginAttempts(blockedFor);
        }

        // An unknown email and a wrong password get the same answer, so the form cannot be used to find out
        // which addresses are registered. Both count as failures.
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            throw failedLogin(email, clientIp, invalidCredentials());
        }

        if (user.getPassword() == null) {
            throw failedLogin(email, clientIp,
                    ApiException.badRequest(ErrorCodes.LOGIN_USE_GOOGLE, "Этот аккаунт зарегистрирован через Google. Войдите через Google."));
        }

        // Checked before the verification flag so the flag is not revealed to someone without the password.
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException ex) {
            throw failedLogin(email, clientIp, invalidCredentials());
        }
        loginAttemptLimiter.recordSuccess(email, clientIp);

        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, EMAIL_NOT_VERIFIED, ErrorCodes.EMAIL_NOT_VERIFIED, null);
        }

        return toAuthResponse(user, jwtTokenProvider.generateToken(user.getEmail(), user.getRole()));
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());

        // Same response whether or not the account exists, to prevent email enumeration.
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            if (user.getPassword() == null) {
                log.info("Password reset requested for Google-only account {}; ignored", email);
                return;
            }
            if (user.getResetOtp().secondsUntilResendAllowed(resendCooldown) > 0) {
                log.info("Password reset for {} requested again within cooldown; not resent", email);
                return;
            }
            String otp = generateOtp();
            user.getResetOtp().issue(otp, otpTtl);
            userRepository.save(user);
            // If sending fails, EmailSendException rolls back the OTP and returns 503.
            emailService.sendPasswordResetOtp(user.getEmail(), user.getFullName(), otp);
        }, () -> log.info("Password reset requested for unknown email {}", email));

        return new MessageResponse(FORGOT_PASSWORD_RESPONSE);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse verifyOtp(VerifyOtpRequest request) {
        User user = findForReset(request.email());
        checkOtp(user, user.getResetOtp(), request.otp());
        return new MessageResponse("Code verified");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = findForReset(request.email());
        checkOtp(user, user.getResetOtp(), request.otp());

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_UNCHANGED, "New password must differ from the current one");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.getResetOtp().clear();
        // The reset code was delivered to this mailbox, which proves ownership of the email.
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.getEmailVerificationOtp().clear();
        }
        userRepository.save(user);

        log.info("Password reset for {}", user.getEmail());
        return new MessageResponse("Password has been reset successfully");
    }

    private void issueVerificationOtp(User user) {
        String otp = generateOtp();
        user.getEmailVerificationOtp().issue(otp, otpTtl);
        userRepository.save(user);
        emailService.sendEmailVerificationOtp(user.getEmail(), user.getFullName(), otp);
    }

    private User findForReset(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> ApiException.badRequest(ErrorCodes.OTP_INVALID, INVALID_OTP));
    }

    private void checkOtp(User user, OtpCode otp, String candidate) {
        if (!otp.isPresent()) {
            throw ApiException.badRequest(ErrorCodes.OTP_INVALID, INVALID_OTP);
        }
        if (otp.isExpired()) {
            otp.clear();
            userRepository.save(user);
            throw ApiException.badRequest(ErrorCodes.OTP_EXPIRED, "Code has expired. Please request a new one.");
        }
        if (!otp.matches(candidate)) {
            if (otp.registerFailedAttempt() >= otpMaxAttempts) {
                otp.clear();
                userRepository.save(user);
                throw ApiException.badRequest(ErrorCodes.OTP_ATTEMPTS_EXCEEDED, "Too many failed attempts. Please request a new code.");
            }
            userRepository.save(user);
            throw ApiException.badRequest(ErrorCodes.OTP_INVALID, INVALID_OTP);
        }
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    /**
     * Counts the failure and returns what to throw: {@code error} normally, 429 when this very failure locked the
     * pair or the IP (the password was wrong, so this tells no more than the next attempt would). Lockouts are
     * audited; ordinary failures are not.
     */
    private ApiException failedLogin(String email, String clientIp, ApiException error) {
        LoginAttemptLimiter.Outcome outcome = loginAttemptLimiter.recordFailure(email, clientIp);
        if (outcome == LoginAttemptLimiter.Outcome.COUNTED) {
            return error;
        }
        boolean ipLocked = outcome == LoginAttemptLimiter.Outcome.IP_LOCKED;
        auditService.record(email, AuditService.LOGIN_LOCKED, (ipLocked
                ? "IP " + clientIp + " blocked after " + LoginAttemptLimiter.IP_MAX_FAILURES + " failed logins"
                : "Email blocked from IP " + clientIp + " after " + LoginAttemptLimiter.PAIR_MAX_FAILURES
                        + " failed logins") + " within " + LoginAttemptLimiter.WINDOW.toMinutes() + " min");
        log.warn("Login {} blocked for {} min after repeated failures (email {}, IP {})",
                ipLocked ? "from IP" : "for email and IP", LoginAttemptLimiter.BLOCK.toMinutes(), email, clientIp);
        return tooManyLoginAttempts(LoginAttemptLimiter.BLOCK.toSeconds());
    }

    private static ApiException tooManyLoginAttempts(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "Слишком много неудачных попыток входа. Попробуйте снова через " + minutes + " мин.",
                ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS, seconds);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, ErrorCodes.INVALID_CREDENTIALS, null);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole(), user.isTeacherRequested());
    }
}
