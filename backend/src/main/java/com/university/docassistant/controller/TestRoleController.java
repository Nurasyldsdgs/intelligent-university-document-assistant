package com.university.docassistant.controller;

import com.university.docassistant.dto.AuthDtos.MessageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for checking role-based access; /public is opened in SecurityConfig, the rest need a JWT.
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestRoleController {

    @GetMapping("/public")
    public MessageResponse publicAccess() {
        return new MessageResponse("Public content");
    }

    @GetMapping("/student")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'ADMIN')")
    public MessageResponse studentAccess() {
        return new MessageResponse("Student content");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse adminAccess() {
        return new MessageResponse("Admin content");
    }
}
