package com.university.docassistant.service;

import com.university.docassistant.entity.DocumentCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentClassificationServiceTest {

    private final AiClientService ai = mock(AiClientService.class);
    private final DocumentClassificationService service = new DocumentClassificationService(ai);

    @Test
    void parsesTheTypeOutOfTheAnswer() {
        assertThat(DocumentClassificationService.parse("SYLLABUS")).contains(DocumentCategory.SYLLABUS);
        assertThat(DocumentClassificationService.parse("Тип: transcript.")).contains(DocumentCategory.TRANSCRIPT);
        assertThat(DocumentClassificationService.parse("**Thesis**")).contains(DocumentCategory.THESIS);
        assertThat(DocumentClassificationService.parse("не знаю")).isEmpty();
        // Retired types are not accepted even if the model names them.
        assertThat(DocumentClassificationService.parse("GRANT_PROPOSAL")).isEmpty();
    }

    @Test
    void sendsFileNameAndOnlyTheFirstThousandCharacters() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of("ASSIGNMENT"));

        assertThat(service.classify("lab4.pdf", "a".repeat(5_000))).contains(DocumentCategory.ASSIGNMENT);

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(ai).chat(anyString(), user.capture());
        assertThat(user.getValue()).startsWith("Имя файла: lab4.pdf").contains("a".repeat(1_000))
                .doesNotContain("a".repeat(1_001));
    }

    @Test
    void withoutAiThereIsNoCallAndNoType() {
        when(ai.isConfigured()).thenReturn(false);

        assertThat(service.classify("x.pdf", "text")).isEmpty();
        verify(ai, never()).chat(anyString(), anyString());
    }
}
