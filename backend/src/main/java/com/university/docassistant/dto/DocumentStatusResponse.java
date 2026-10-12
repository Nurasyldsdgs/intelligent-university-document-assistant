package com.university.docassistant.dto;

import com.university.docassistant.entity.AiStatus;
import com.university.docassistant.entity.DocumentCategory;

/**
 * Background AI state of one document (GET /api/v1/documents/{id}/status), polled after an upload or a reclassify.
 *
 * @param aiConfidence     the AI's confidence 0..1 when aiStatus is DONE and it gave one; null otherwise
 * @param assignmentsCount assignments found in this document by the deadline analysis
 */
public record DocumentStatusResponse(
        AiStatus aiStatus,
        DocumentCategory category,
        Double aiConfidence,
        long assignmentsCount
) {
}
