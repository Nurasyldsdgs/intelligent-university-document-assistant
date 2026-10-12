package com.university.docassistant.repository;

import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentVisibility;
import com.university.docassistant.entity.User;

import java.time.LocalDateTime;

/**
 * A document row for lists: everything a list shows, without the (up to 100 000 characters) extracted text.
 * Built by the constructor expressions in {@link DocumentRepository}.
 */
public record DocumentSummary(
        Long id,
        String originalFileName,
        String contentType,
        Long fileSize,
        DocumentCategory category,
        DocumentVisibility visibility,
        LocalDateTime uploadedAt,
        String textSnippet,
        Long ownerId,
        String ownerEmail,
        String ownerFullName,
        Long courseId,
        String courseTitle,
        String courseCode
) {
    /** Select list matching the canonical constructor; the queries alias Document as d, owner as o, course as c. */
    static final String SELECT = """
            SELECT new com.university.docassistant.repository.DocumentSummary(
                d.id, d.originalFileName, d.contentType, d.fileSize, d.category, d.visibility, d.uploadedAt,
                d.textSnippet, o.id, o.email, o.fullName, c.id, c.title, c.code)
            FROM Document d JOIN d.owner o LEFT JOIN d.course c
            """;

    public static DocumentSummary of(Document document) {
        User owner = document.getOwner();
        Course course = document.getCourse();
        return new DocumentSummary(
                document.getId(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getCategory(),
                document.getVisibility(),
                document.getUploadedAt(),
                document.getTextSnippet(),
                owner.getId(),
                owner.getEmail(),
                owner.getFullName(),
                course != null ? course.getId() : null,
                course != null ? course.getTitle() : null,
                course != null ? course.getCode() : null);
    }
}
