package com.university.docassistant.controller;

import com.university.docassistant.dto.DocumentAnswerResponse;
import com.university.docassistant.service.AiClientService;
import com.university.docassistant.service.DocumentQnaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiClientService aiClientService;
    private final DocumentQnaService documentQnaService;

    /**
     * Lets the UI disable AI features up front instead of showing an error after the first question.
     * {@code available} only says the AI is configured; a call can still fail with 503 if the provider is down.
     */
    public record AiStatusResponse(boolean available, boolean enabled) {
    }

    /** Without {@code courseId} the question is answered from all of the user's courses. */
    public record AskRequest(@NotBlank @Size(max = 1_000) String question, Long courseId) {
    }

    @GetMapping("/status")
    public AiStatusResponse status() {
        return new AiStatusResponse(aiClientService.isConfigured(), aiClientService.isEnabled());
    }

    /** Answers only from documents of courses the user may access (403 for a course that is not theirs). */
    @PostMapping("/ask")
    public DocumentAnswerResponse ask(@Valid @RequestBody AskRequest request,
                                      @AuthenticationPrincipal UserDetails principal) {
        return request.courseId() == null
                ? documentQnaService.askAll(request.question(), principal.getUsername())
                : documentQnaService.askCourse(request.courseId(), request.question(), principal.getUsername());
    }
}
