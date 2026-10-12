package com.university.docassistant.dto;

import com.university.docassistant.entity.Role;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Request/response DTOs for the admin panel.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserResponse(
            Long id,
            String fullName,
            String email,
            Role role,
            boolean emailVerified,
            LocalDateTime createdAt
    ) {
    }

    /** A registered STUDENT waiting for the teacher role; {@code requestedAt} is the registration time. */
    public record TeacherRequestResponse(
            Long id,
            String fullName,
            String email,
            boolean emailVerified,
            LocalDateTime requestedAt
    ) {
    }

    public record UpdateRoleRequest(
            @NotNull Role role
    ) {
    }

    public record AdminStatsResponse(
            long totalUsers,
            long totalDocuments,
            long totalStorageBytes,
            long totalCourses,
            long totalEnrollments,
            long totalAssignments
    ) {
    }

    public record AuditLogResponse(
            Long id,
            LocalDateTime createdAt,
            String actorEmail,
            String action,
            String details
    ) {
    }
}
