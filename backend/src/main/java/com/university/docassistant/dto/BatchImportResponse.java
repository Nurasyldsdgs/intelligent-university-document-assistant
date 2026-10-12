package com.university.docassistant.dto;

import java.time.LocalDateTime;

/**
 * One ZIP import of a course (GET /api/v1/courses/{courseId}/imports).
 *
 * @param totalFiles     files found in the archive, including skipped ones
 * @param processedFiles files that became documents of the course
 * @param facultyName    who uploaded it; null once that account is deleted
 * @param pendingCount   documents of this import the AI is still classifying (PENDING or PROCESSING)
 */
public record BatchImportResponse(
        Long id,
        String archiveName,
        int totalFiles,
        int processedFiles,
        String facultyName,
        LocalDateTime createdAt,
        long pendingCount
) {
}
