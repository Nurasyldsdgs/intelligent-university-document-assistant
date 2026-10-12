package com.university.docassistant.service;

import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.DocumentRepository.AnalysisSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** EXAM_ANSWERS are skipped even when the analysis is invoked directly, bypassing DocumentService's 400. */
class DocumentAnalysisServiceTest {

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final AssignmentService assignments = mock(AssignmentService.class);
    private final AiTaskExtractionService extraction = mock(AiTaskExtractionService.class);
    private final DocumentAnalysisService service = new DocumentAnalysisService(documents, assignments, extraction,
            mock(PlatformTransactionManager.class));

    @Test
    void manualReanalysisOfExamAnswersNeverCallsTheAiOrCreatesAssignments() {
        when(documents.findAnalysisSource(7L)).thenReturn(Optional.of(
                new AnalysisSource(7L, DocumentCategory.EXAM_ANSWERS, "Задание 1: ответ ... срок 15.12", 1L, 10L)));

        service.reanalyzeAsync(7L); // Called directly: no Spring proxy, so it runs synchronously.

        verify(extraction, never()).analyze(anyString());
        verifyNoInteractions(assignments);
        verify(documents, never()).lockById(any()); // Nothing is written either.
    }
}
