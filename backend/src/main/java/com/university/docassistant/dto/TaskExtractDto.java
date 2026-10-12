package com.university.docassistant.dto;

import java.time.LocalDateTime;

/**
 * One assignment, lab or deadline found in a document by the AI; {@code deadline} is null when the text gives none.
 */
public record TaskExtractDto(
        String title,
        String description,
        LocalDateTime deadline
) {
}
