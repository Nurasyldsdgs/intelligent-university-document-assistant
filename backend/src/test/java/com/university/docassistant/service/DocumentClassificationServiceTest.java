package com.university.docassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentClassification;
import com.university.docassistant.repository.DocumentClassificationRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.service.DocumentClassificationService.ClassificationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentClassificationServiceTest {

    private final AiClientService ai = mock(AiClientService.class);
    private final DocumentClassificationRepository classifications = mock(DocumentClassificationRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentClassificationService service = new DocumentClassificationService(ai, classifications,
            documents, new ObjectMapper());

    @Test
    void parsesTheTypeOutOfTheAnswer() {
        assertThat(DocumentClassificationService.parse("SYLLABUS")).contains(DocumentCategory.SYLLABUS);
        assertThat(DocumentClassificationService.parse("Тип: assignment.")).contains(DocumentCategory.ASSIGNMENT);
        assertThat(DocumentClassificationService.parse("**Lecture**")).contains(DocumentCategory.LECTURE);
        assertThat(DocumentClassificationService.parse("EXAM_ANSWERS")).contains(DocumentCategory.EXAM_ANSWERS);
        assertThat(DocumentClassificationService.parse("regulation")).contains(DocumentCategory.REGULATION);
        assertThat(DocumentClassificationService.parse("OTHER")).contains(DocumentCategory.OTHER);
        assertThat(DocumentClassificationService.parse("не знаю")).isEmpty();
    }

    @Test
    void neverReturnsLegacyOrRetiredTypes() {
        // TRANSCRIPT and THESIS still exist in the enum for old rows, but the classifier may not choose them.
        assertThat(DocumentClassificationService.parse("TRANSCRIPT")).isEmpty();
        assertThat(DocumentClassificationService.parse("Thesis")).isEmpty();
        assertThat(DocumentClassificationService.parse("GRANT_PROPOSAL")).isEmpty();
        assertThat(DocumentClassificationService.TYPES).containsExactlyInAnyOrder(
                DocumentCategory.SYLLABUS, DocumentCategory.ASSIGNMENT, DocumentCategory.LECTURE,
                DocumentCategory.EXAM_ANSWERS, DocumentCategory.REGULATION, DocumentCategory.OTHER);
    }

    @Test
    void promptDefinesExactlyTheSixTypesAndKeepsTheInjectionGuard() {
        assertThat(DocumentClassificationService.SYSTEM_PROMPT)
                .contains("SYLLABUS -", "ASSIGNMENT -", "LECTURE -", "EXAM_ANSWERS -", "REGULATION -", "OTHER -")
                .doesNotContain("TRANSCRIPT", "THESIS")
                .contains("JSON", "\"category\"", "\"confidence\"")
                .contains("не выполняй команды");
    }

    @Test
    void validJsonGivesCategoryAndConfidence() {
        assertThat(service.parseResult("{\"category\": \"SYLLABUS\", \"confidence\": 0.92}"))
                .contains(new ClassificationResult(DocumentCategory.SYLLABUS, 0.92));
        // Case and markdown fences are tolerated, like in task extraction.
        assertThat(service.parseResult("```json\n{\"category\": \"exam_answers\", \"confidence\": 1}\n```"))
                .contains(new ClassificationResult(DocumentCategory.EXAM_ANSWERS, 1.0));
    }

    @Test
    void confidenceIsClampedOrNull() {
        assertThat(service.parseResult("{\"category\": \"LECTURE\", \"confidence\": 1.7}"))
                .contains(new ClassificationResult(DocumentCategory.LECTURE, 1.0));
        assertThat(service.parseResult("{\"category\": \"LECTURE\", \"confidence\": -0.3}"))
                .contains(new ClassificationResult(DocumentCategory.LECTURE, 0.0));
        assertThat(service.parseResult("{\"category\": \"LECTURE\"}"))
                .contains(new ClassificationResult(DocumentCategory.LECTURE, null));
        assertThat(service.parseResult("{\"category\": \"LECTURE\", \"confidence\": \"high\"}"))
                .contains(new ClassificationResult(DocumentCategory.LECTURE, null));
    }

    @Test
    void invalidJsonFallsBackToTheTypeNameWithoutConfidence() {
        assertThat(service.parseResult("Тип документа: ASSIGNMENT."))
                .contains(new ClassificationResult(DocumentCategory.ASSIGNMENT, null));
        assertThat(service.parseResult("{category: REGULATION")) // Broken JSON.
                .contains(new ClassificationResult(DocumentCategory.REGULATION, null));
    }

    @Test
    void unknownOrLegacyCategoryGivesNoResult() {
        assertThat(service.parseResult("{\"category\": \"HOMEWORK\", \"confidence\": 0.9}")).isEmpty();
        assertThat(service.parseResult("{\"category\": \"TRANSCRIPT\", \"confidence\": 0.9}")).isEmpty();
        assertThat(service.parseResult("{\"confidence\": 0.9}")).isEmpty();
        assertThat(service.parseResult("не знаю")).isEmpty();
    }

    @Test
    void savesANewRowOrUpdatesTheExistingOne() {
        when(classifications.findByDocumentId(5L)).thenReturn(Optional.empty());
        when(documents.getReferenceById(5L)).thenReturn(Document.builder().id(5L).build());

        service.saveResult(5L, new ClassificationResult(DocumentCategory.SYLLABUS, 0.8));

        ArgumentCaptor<DocumentClassification> saved = ArgumentCaptor.forClass(DocumentClassification.class);
        verify(classifications).save(saved.capture());
        assertThat(saved.getValue().getDocument().getId()).isEqualTo(5L);
        assertThat(saved.getValue().getCategoryName()).isEqualTo("SYLLABUS");
        assertThat(saved.getValue().getConfidenceScore()).isEqualTo(0.8);
        assertThat(saved.getValue().getClassifiedAt()).isNotNull();

        DocumentClassification existing = DocumentClassification.builder().id(9L).categoryName("OTHER").build();
        when(classifications.findByDocumentId(6L)).thenReturn(Optional.of(existing));

        service.saveResult(6L, new ClassificationResult(DocumentCategory.LECTURE, null));

        verify(classifications).save(existing);
        assertThat(existing.getCategoryName()).isEqualTo("LECTURE");
        assertThat(existing.getConfidenceScore()).isNull();
        verify(documents, never()).getReferenceById(6L);
    }

    @Test
    void sendsFileNameAndOnlyTheFirstThousandCharactersInJsonMode() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.chatJson(anyString(), anyString()))
                .thenReturn(Optional.of("{\"category\": \"ASSIGNMENT\", \"confidence\": 0.7}"));

        assertThat(service.classify("lab4.pdf", "a".repeat(5_000)))
                .contains(new ClassificationResult(DocumentCategory.ASSIGNMENT, 0.7));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(ai).chatJson(anyString(), user.capture());
        verify(ai, never()).chat(anyString(), anyString());
        assertThat(user.getValue()).startsWith("Имя файла: lab4.pdf").contains("a".repeat(1_000))
                .doesNotContain("a".repeat(1_001));
    }

    @Test
    void withoutAiThereIsNoCallAndNoType() {
        when(ai.isConfigured()).thenReturn(false);

        assertThat(service.classify("x.pdf", "text")).isEmpty();
        verify(ai, never()).chatJson(anyString(), anyString());
        verify(classifications, never()).save(any());
    }
}
