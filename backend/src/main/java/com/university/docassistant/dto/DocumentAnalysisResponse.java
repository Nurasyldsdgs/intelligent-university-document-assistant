package com.university.docassistant.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Latest AI analysis of a document. {@code analyzedAt} is null until an analysis has succeeded; it changes on every
 * successful run, which is what a client polls for after POST /analyze.
 *
 * @param aiAvailable  whether the AI is configured on the server at all
 * @param canAnalyze   whether the current user may start an analysis (owner, course teacher or ADMIN)
 * @param autoAnalyzed whether the document's category is analyzed automatically after upload
 */
public record DocumentAnalysisResponse(
        String summary,
        List<String> topics,
        LocalDateTime analyzedAt,
        boolean hasText,
        boolean aiAvailable,
        boolean canAnalyze,
        boolean autoAnalyzed
) {
}
