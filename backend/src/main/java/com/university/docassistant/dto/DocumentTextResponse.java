package com.university.docassistant.dto;

/**
 * Full extracted text of a document; {@code text} is null when nothing could be extracted
 * (a scan without a text layer, a password-protected or corrupted file, an unsupported format).
 * {@code truncated} means the text hit the 100 000-character limit and the rest of the file was not read.
 */
public record DocumentTextResponse(
        Long id,
        String originalFileName,
        boolean hasExtractedText,
        boolean truncated,
        String text
) {
}
