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
    private static final String EMAIL_NOT_VERIFIED = "Пожалуйста, подтвердите ваш email перед входом";
    private static final String EMAIL_ALREADY_VERIFIED = "Email уже подтверждён. Войдите в систему.";
    private static final String EMAIL_TAKEN =
            "Пользователь с таким email уже зарегистрирован. Попробуйте войти или используйте другой email.";
    private static final String INVALID_CREDENTIALS = "Неверный email или пароль. Проверьте данные или восстановите доступ.";
    private static final String FORGOT_PASSWORD_RESPONSE =
            "If an account with this email exists, a reset code has been sent";

    private final UserRepository userRepository;
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
                       @Value("${app.otp.max-attempts}") int otpMaxAttempts) {
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
     */
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        Role role = request.role() != null ? request.role() : Role.ROLE_STUDENT;

        if (role == Role.ROLE_ADMIN) {
            throw ApiException.forbidden("Роль «Деканат» нельзя выбрать при самостоятельной регистрации. "
                    + "Обратитесь к администратору системы.");
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
                .role(role)
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();

        // If sending fails, EmailSendException rolls back the whole registration and returns 503.
        issueVerificationOtp(user);
        log.info("Registered new user {} with role {}, awaiting email verification", email, role);
        return new MessageResponse(VERIFICATION_SENT);
    }

    // noRollbackFor: failed attempts must be persisted even though we throw.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> ApiException.badRequest(INVALID_OTP));

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
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        // An unknown email and a wrong password get the same answer, so the form cannot be used to find out
        // which addresses are registered.
        User user = userRepository.findByEmail(email).orElseThrow(AuthService::invalidCredentials);

        if (user.getPassword() == null) {
            throw ApiException.badRequest("Этот аккаунт зарегистрирован через Google. Войдите через Google.");
        }

        // Checked before the verification flag so the flag is not revealed to someone without the password.
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException ex) {
            throw invalidCredentials();
        }

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
            throw ApiException.badRequest("New password must differ from the current one");
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
                .orElseThrow(() -> ApiException.badRequest(INVALID_OTP));
    }

    private void checkOtp(User user, OtpCode otp, String candidate) {
        if (!otp.isPresent()) {
            throw ApiException.badRequest(INVALID_OTP);
        }
        if (otp.isExpired()) {
            otp.clear();
            userRepository.save(user);
            throw ApiException.badRequest("Code has expired. Please request a new one.");
        }
        if (!otp.matches(candidate)) {
            if (otp.registerFailedAttempt() >= otpMaxAttempts) {
                otp.clear();
                userRepository.save(user);
                throw ApiException.badRequest("Too many failed attempts. Please request a new code.");
            }
            userRepository.save(user);
            throw ApiException.badRequest(INVALID_OTP);
        }
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, ErrorCodes.INVALID_CREDENTIALS, null);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole());
    }
}
