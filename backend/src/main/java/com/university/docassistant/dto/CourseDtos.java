package com.university.docassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request/response DTOs for courses.
 */
public final class CourseDtos {

    private CourseDtos() {
    }

    /** {@code teacherId} is for ADMIN only; a TEACHER always creates a course they teach themselves. */
    public record CreateCourseRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 50) String code,
            @Size(max = 10_000) String description,
            Long teacherId
    ) {
    }

    /** Without {@code studentId} the current user enrolls themselves. */
    /** Body of POST /courses/join: the code from the invitation link /join/{code}. */
    public record JoinCourseRequest(
            @NotBlank(message = "Укажите код приглашения") @Size(max = 200) String code
    ) {
    }

    public record JoinCourseResponse(
            Long courseId
    ) {
    }

    /** Only for the course's teacher and ADMIN; students never receive it. */
    public record InviteCodeResponse(
            String inviteCode
    ) {
    }

    public record EnrollRequest(
            Long studentId
    ) {
    }

    public record AssignTeacherRequest(
            @NotNull Long teacherId
    ) {
    }

    public record CourseResponse(
            Long id,
            String title,
            String code,
            String description,
            Long teacherId,
            String teacherFullName,
            String teacherEmail
    ) {
    }

    /**
     * @param enrolled  whether the current user is a student of the course
     * @param canManage whether the current user is the course's teacher or ADMIN
     */
    public record CourseDetailsResponse(
            CourseResponse course,
            long studentCount,
            boolean enrolled,
            boolean canManage
    ) {
    }

    public record StudentResponse(
            Long id,
            String fullName,
            String email
    ) {
    }
}
