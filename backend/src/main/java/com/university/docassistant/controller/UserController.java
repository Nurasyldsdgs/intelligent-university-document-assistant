package com.university.docassistant.controller;

import com.university.docassistant.dto.AuthDtos.UserResponse;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.exception.ErrorCodes;
import com.university.docassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .map(user -> ResponseEntity.ok(new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole(),
                        user.getProvider(),
                        user.isEmailVerified(),
                        user.isTeacherRequested())))
                .orElseThrow(() -> ApiException.unauthorized(ErrorCodes.ACCOUNT_NOT_FOUND, "User no longer exists"));
    }
}
