package com.university.docassistant.security;

import com.university.docassistant.entity.AuthProvider;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final String redirectUri;

    public OAuth2SuccessHandler(UserRepository userRepository,
                                JwtTokenProvider jwtTokenProvider,
                                @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redirectUri = frontendUrl + "/oauth2/redirect";
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String rawEmail = oauth2User.getAttribute("email");

        if (rawEmail == null || rawEmail.isBlank()) {
            log.warn("Google OAuth2 login without email attribute");
            redirect(request, response, "error", "email_not_provided");
            return;
        }

        String email = rawEmail.trim().toLowerCase();
        String name = oauth2User.getAttribute("name");
        boolean googleVerified = Boolean.TRUE.equals(oauth2User.getAttribute("email_verified"));

        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    // Existing LOCAL account signs in via Google: keep provider and password, trust Google's verification.
                    if (googleVerified && !existing.isEmailVerified()) {
                        existing.setEmailVerified(true);
                    }
                    if (existing.getFullName() == null && name != null) {
                        existing.setFullName(name);
                    }
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.info("Registering new Google user {}", email);
                    return userRepository.save(User.builder()
                            .email(email)
                            .fullName(name != null ? name : email)
                            .role(Role.ROLE_STUDENT)
                            .provider(AuthProvider.GOOGLE)
                            .emailVerified(googleVerified)
                            .build());
                });

        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole());
        redirect(request, response, "token", token);
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response,
                          String param, String value) throws IOException {
        // The session only existed to hold the OAuth2 authorization request; the API itself is stateless.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam(param, value)
                .build()
                .encode()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
