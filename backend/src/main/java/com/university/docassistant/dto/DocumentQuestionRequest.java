package com.university.docassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentQuestionRequest(
        @NotBlank @Size(max = 1_000) String question
) {
}
