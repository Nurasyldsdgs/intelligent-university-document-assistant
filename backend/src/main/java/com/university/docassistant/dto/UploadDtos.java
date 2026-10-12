package com.university.docassistant.dto;

import java.util.List;

/**
 * Responses of the multi-file and ZIP uploads.
 */
public final class UploadDtos {

    private UploadDtos() {
    }

    /**
     * {@code aiClassified} is always false now: the AI classifies in the background after the upload has answered
     * (see DocumentResponse.aiStatus and GET /documents/{id}/status). Kept so existing clients keep working.
     */
    public record UploadedDocument(DocumentResponse document, boolean aiClassified) {
    }

    public record SkippedFile(String fileName, String reason) {
    }

    /** {@code batchImportId} is the BatchImport row of a ZIP upload; null for the multi-file upload. */
    public record BatchUploadResponse(List<UploadedDocument> uploaded, List<SkippedFile> skipped, Long batchImportId) {
    }
}
