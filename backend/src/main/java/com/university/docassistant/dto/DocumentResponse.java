package com.university.docassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentVisibility;

import java.time.LocalDateTime;

/**
 * Document metadata for the vault list. {@code canDelete} and {@code isOwner} are computed for the requesting user
 * so the frontend does not have to duplicate the permission rules. The course fields are null for documents
 * not tied to a course. {@code textSnippet} holds the first 200 characters of the extracted text; the full text
 * is served by GET /api/v1/documents/{id}/text.
 */
public record DocumentResponse(
        Long id,
        String originalFileName,
        String contentType,
        long fileSize,
        String ownerEmail,
        String ownerFullName,
        LocalDateTime uploadedAt,
        boolean canDelete,
        DocumentCategory category,
        DocumentVisibility visibility,
        Long courseId,
        String courseTitle,
        String courseCode,
        boolean hasExtractedText,
        String textSnippet,
        // Explicit name: Jackson would otherwise serialize a boolean "isX" accessor as "x".
        @JsonProperty("isOwner") boolean isOwner
) {
}
