package com.university.docassistant.dto;

import java.util.List;

/**
 * Responses of the multi-file and ZIP uploads.
 */
public final class UploadDtos {

    private UploadDtos() {
    }

    /** {@code aiClassified} is true when the category was chosen by the AI classifier rather than by the uploader. */
    public record UploadedDocument(DocumentResponse document, boolean aiClassified) {
    }

    public record SkippedFile(String fileName, String reason) {
    }

    public record BatchUploadResponse(List<UploadedDocument> uploaded, List<SkippedFile> skipped) {
    }
}
