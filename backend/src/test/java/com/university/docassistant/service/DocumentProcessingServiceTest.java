package com.university.docassistant.service;

import com.university.docassistant.entity.AiStatus;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentType;
import com.university.docassistant.repository.DocumentClassificationRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.DocumentRepository.ClassificationSource;
import com.university.docassistant.service.DocumentClassificationService.ClassificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Background classification: claim PENDING, classify, store the outcome, then the deadline analysis. */
class DocumentProcessingServiceTest {

    private static final long ID = 100L;
    private static final String TEXT = "Силлабус курса «Алгоритмы». Лабораторная 1 — до 15.11.";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentClassificationRepository classifications = mock(DocumentClassificationRepository.class);
    private final DocumentClassificationService classifier = mock(DocumentClassificationService.class);
    private final DocumentAnalysisService analysis = mock(DocumentAnalysisService.class);
    private final AuditService audit = mock(AuditService.class);
    // A mock manager: TransactionTemplate then simply runs the callback.
    private final DocumentProcessingService service = new DocumentProcessingService(documents, classifications,
            classifier, analysis, audit, mock(PlatformTransactionManager.class), mock(DocumentOcrService.class));

    @BeforeEach
    void setUp() {
        when(documents.changeAiStatus(ID, AiStatus.PENDING, AiStatus.PROCESSING)).thenReturn(1);
        when(documents.findClassificationSource(ID))
                .thenReturn(Optional.of(new ClassificationSource(ID, "syllabus.pdf", TEXT, "teacher@uni.kz")));
        when(documents.updateClassification(eq(ID), any(), any(), any())).thenReturn(1);
    }

    @Test
    void classifiesStoresTheConfidenceAndRunsTheDeadlineAnalysis() {
        ClassificationResult result = new ClassificationResult(DocumentCategory.SYLLABUS, 0.93);
        when(classifier.classify("syllabus.pdf", TEXT)).thenReturn(Optional.of(result));

        service.process(ID);

        verify(documents).updateClassification(ID, DocumentCategory.SYLLABUS, DocumentType.SYLLABUS, AiStatus.DONE);
        verify(classifier).saveResult(ID, result);
        verify(documents, never()).forceTeacherOnly(anyLong());
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq("teacher@uni.kz"), eq(AuditService.DOCUMENT_CLASSIFIED), details.capture());
        assertThat(details.getValue()).contains("SYLLABUS", "0.93").doesNotContain("forced");
        // In this same thread, not through another @Async job.
        verify(analysis).analyzeNow(ID, true);
        verify(analysis, never()).analyzeAndCreateTasksAsync(any());
    }

    @Test
    void examAnswersResultFlipsVisibilityToTeacherOnlyAndIsNeverAnalyzed() {
        when(classifier.classify(anyString(), anyString()))
                .thenReturn(Optional.of(new ClassificationResult(DocumentCategory.EXAM_ANSWERS, 0.8)));
        when(documents.forceTeacherOnly(ID)).thenReturn(1);

        service.process(ID);

        verify(documents).updateClassification(ID, DocumentCategory.EXAM_ANSWERS, DocumentType.EXAM_ANSWERS,
                AiStatus.DONE);
        verify(documents).forceTeacherOnly(ID);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit).record(anyString(), eq(AuditService.DOCUMENT_CLASSIFIED), details.capture());
        assertThat(details.getValue()).endsWith(", visibility forced to TEACHER_ONLY");
        verifyNoInteractions(analysis);
    }

    @Test
    void noAnswerFromTheAiMeansFailedWithCategoryOther() {
        when(classifier.classify(anyString(), anyString())).thenReturn(Optional.empty());

        service.process(ID);

        verify(documents).updateClassification(ID, DocumentCategory.OTHER, DocumentType.OTHER, AiStatus.FAILED);
        verify(classifier, never()).saveResult(any(), any());
        verifyNoInteractions(analysis);
    }

    @Test
    void anExceptionMarksTheDocumentFailedAndIsNotRethrown() {
        when(classifier.classify(anyString(), anyString()))
                .thenReturn(Optional.of(new ClassificationResult(DocumentCategory.LECTURE, 0.5)));
        when(documents.updateClassification(eq(ID), any(), any(), any()))
                .thenThrow(new IllegalStateException("database down"));

        service.process(ID); // Must not throw.

        verify(documents).setAiStatus(ID, AiStatus.FAILED);
        verifyNoInteractions(analysis);
    }

    @Test
    void aDocumentThatIsNotPendingIsLeftAlone() {
        when(documents.changeAiStatus(ID, AiStatus.PENDING, AiStatus.PROCESSING)).thenReturn(0);

        service.process(ID);

        verify(documents, never()).findClassificationSource(any());
        verifyNoInteractions(classifier, analysis, audit);
        verify(documents, never()).setAiStatus(any(), any());
    }

    @Test
    void aDocumentDeletedDuringClassificationGetsNoResultRow() {
        when(classifier.classify(anyString(), anyString()))
                .thenReturn(Optional.of(new ClassificationResult(DocumentCategory.ASSIGNMENT, 0.7)));
        when(documents.updateClassification(eq(ID), any(), any(), any())).thenReturn(0);

        service.process(ID);

        verify(classifier, never()).saveResult(any(), any());
        verifyNoInteractions(analysis, audit);
    }
}
