package com.university.docassistant.dto;

import java.util.List;

/**
 * Answer of the Q&A assistant. {@code hasAnswer} is false when the documents do not cover the question (or have no
 * text); {@code sources} are then empty. Otherwise they are the fragments the answer cites.
 */
public record DocumentAnswerResponse(
        String answer,
        boolean hasAnswer,
        List<SourceSnippetDto> sources
) {
}
