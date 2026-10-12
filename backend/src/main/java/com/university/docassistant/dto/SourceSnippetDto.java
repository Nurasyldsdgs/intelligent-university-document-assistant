package com.university.docassistant.dto;

/**
 * A fragment of a document the answer is based on. {@code fragment} is the number the answer cites as [n];
 * offsets point into the document's extracted text (GET /api/v1/documents/{id}/text), end exclusive.
 */
public record SourceSnippetDto(
        int fragment,
        Long documentId,
        String documentName,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String text
) {
}
