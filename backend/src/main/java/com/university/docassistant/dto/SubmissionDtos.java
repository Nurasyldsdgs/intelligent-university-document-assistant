package com.university.docassistant.dto;

import java.time.LocalDateTime;

/**
 * Responses for students' task submissions.
 */
public final class SubmissionDtos {

    private SubmissionDtos() {
    }

    /** Built directly by the JPQL constructor expressions in TaskSubmissionRepository; see {@link #SELECT}. */
    public record SubmissionResponse(
            Long id,
            Long taskId,
            Long assignmentId,
            Long studentId,
            String studentEmail,
            String studentFullName,
            String originalFileName,
            String contentType,
            Long fileSize,
            LocalDateTime submittedAt
    ) {
        /** Select list matching the canonical constructor; the queries alias TaskSubmission as s, Task as t. */
        public static final String SELECT = """
                SELECT new com.university.docassistant.dto.SubmissionDtos$SubmissionResponse(
                    s.id, t.id, a.id, u.id, u.email, u.fullName, s.originalFileName, s.contentType, s.fileSize,
                    s.submittedAt)
                FROM TaskSubmission s JOIN s.task t LEFT JOIN t.assignment a JOIN s.student u
                """;
    }
}
