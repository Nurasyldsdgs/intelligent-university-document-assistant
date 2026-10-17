package com.university.docassistant.dto;

import com.university.docassistant.entity.AssignmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request/response DTOs for course assignments.
 */
public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    /** Built directly by the JPQL constructor expressions in AssignmentRepository; see {@link #SELECT}. */
    public record AssignmentResponse(
            Long id,
            String title,
            String description,
            LocalDateTime deadline,
            AssignmentStatus status,
            Long courseId,
            String courseTitle,
            String courseCode,
            Long sourceDocumentId,
            String sourceDocumentName,
            LocalDateTime createdAt,
            /** For an AI draft: the existing assignment of the course it looks like; null when none. */
            Long duplicateOfId,
            String duplicateOfTitle,
            LocalDateTime duplicateOfDeadline
    ) {
        /** Select list matching the canonical constructor; the queries alias Assignment as a, course as c. */
        public static final String SELECT = """
                SELECT new com.university.docassistant.dto.AssignmentDtos$AssignmentResponse(
                    a.id, a.title, a.requirements, a.deadline, a.status, c.id, c.title, c.code,
                    d.id, d.originalFileName, a.createdAt, dup.id, dup.title, dup.deadline)
                FROM Assignment a JOIN a.course c LEFT JOIN a.sourceDocument d LEFT JOIN a.duplicateOf dup
                """;
    }

    /** Drafts of one course to publish together; all or none. */
    public record PublishRequest(
            @NotEmpty @Size(max = 200) List<@NotNull Long> ids
    ) {
    }

    /** How many drafts became ACTIVE and how many student tasks that created. */
    public record PublishResponse(int published, int tasksCreated) {
    }

    /**
     * PATCH /assignments/{id}: the editable fields, all of them (null description or deadline clears it). The
     * requirements travel as "description", like in {@link CreateAssignmentRequest} and {@link AssignmentResponse}.
     */
    public record UpdateAssignmentRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 2_000) String description,
            LocalDateTime deadline
    ) {
    }

    public record CreateAssignmentRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 2_000) String description,
            LocalDateTime deadline
    ) {
    }
}
