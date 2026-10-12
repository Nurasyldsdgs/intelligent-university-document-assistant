package com.university.docassistant.dto;

import com.university.docassistant.entity.AssignmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

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
            LocalDateTime createdAt
    ) {
        /** Select list matching the canonical constructor; the queries alias Assignment as a, course as c. */
        public static final String SELECT = """
                SELECT new com.university.docassistant.dto.AssignmentDtos$AssignmentResponse(
                    a.id, a.title, a.requirements, a.deadline, a.status, c.id, c.title, c.code,
                    d.id, d.originalFileName, a.createdAt)
                FROM Assignment a JOIN a.course c LEFT JOIN a.sourceDocument d
                """;
    }

    public record CreateAssignmentRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 2_000) String description,
            LocalDateTime deadline
    ) {
    }
}
