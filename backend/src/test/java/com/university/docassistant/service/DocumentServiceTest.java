package com.university.docassistant.service;

import com.university.docassistant.config.PrivacyProperties;
import com.university.docassistant.entity.AiStatus;
import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentType;
import com.university.docassistant.entity.DocumentVisibility;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.repository.AssignmentRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.security.Permission;
import com.university.docassistant.dto.DocumentResponse;
import com.university.docassistant.service.DocumentParserService.ExtractedText;
import com.university.docassistant.dto.UploadDtos.BatchUploadResponse;
import com.university.docassistant.dto.UploadDtos.SkippedFile;
import com.university.docassistant.service.storage.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exam answers never reach students: forced TEACHER_ONLY on upload, never analyzed into assignments. */
class DocumentServiceTest {

    private static final String EMAIL = "teacher@uni.kz";
    private static final String TEXT = "Ответы к экзамену по алгоритмам: 1) O(n log n) 2) ...";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final StorageService storage = mock(StorageService.class);
    private final DocumentParserService parser = mock(DocumentParserService.class);
    private final DocumentAnalysisService analysis = mock(DocumentAnalysisService.class);
    private final AiClientService ai = mock(AiClientService.class);
    private final AuditService audit = mock(AuditService.class);
    private final DocumentClassificationService classifier = mock(DocumentClassificationService.class);
    private final DocumentProcessingService processing = mock(DocumentProcessingService.class);
    private final DocumentService service = new DocumentService(documents, mock(TaskRepository.class),
            mock(AssignmentRepository.class), access, storage, parser, analysis, ai, mock(DocumentChunkService.class),
            audit, classifier, processing, mock(com.university.docassistant.repository.BatchImportRepository.class),
            mock(AssignmentService.class), new DocumentPrivacyService(new PrivacyProperties(null, null)));

    private final User teacher = new User();
    private final Course course = Course.builder().id(10L).title("Algorithms").build();

    @BeforeEach
    void setUp() {
        teacher.setId(2L);
        teacher.setRole(Role.ROLE_TEACHER);
        teacher.setEmail(EMAIL);
        when(access.currentUser(EMAIL)).thenReturn(teacher);
        when(access.authorizeCourse(teacher, 10L, Permission.UPLOAD_DOCUMENT)).thenReturn(course);
        when(parser.extractText(any(InputStream.class), anyString(), anyString()))
                .thenReturn(new ExtractedText(TEXT, false));
        when(storage.store(any(byte[].class))).thenReturn("stored-name");
        when(documents.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(100L);
            return document;
        });
        when(ai.isConfigured()).thenReturn(true);
    }

    @Test
    void manuallyChosenExamAnswersAreForcedToTeacherOnly() {
        service.upload(file("exam-key.pdf"), DocumentCategory.EXAM_ANSWERS, DocumentVisibility.COURSE_STUDENTS, 10L, EMAIL);

        Document saved = savedDocument();
        assertThat(saved.getVisibility()).isEqualTo(DocumentVisibility.TEACHER_ONLY);
        assertThat(saved.getType()).isEqualTo(DocumentType.EXAM_ANSWERS);
        assertThat(auditDetails()).endsWith(", visibility forced to TEACHER_ONLY");
        verifyNoInteractions(analysis);
    }

    @Test
    void uploadWithoutCategoryIsPendingAndNeverWaitsForTheAi() {
        DocumentResponse uploaded = service.upload(file("midterm.pdf"), null, DocumentVisibility.COURSE_STUDENTS,
                10L, EMAIL);

        verifyNoInteractions(classifier);
        assertThat(uploaded.aiStatus()).isEqualTo(AiStatus.PENDING);
        assertThat(uploaded.category()).isEqualTo(DocumentCategory.OTHER);
        Document saved = savedDocument();
        assertThat(saved.getAiStatus()).isEqualTo(AiStatus.PENDING);
        assertThat(saved.getType()).isEqualTo(DocumentType.OTHER);
        verify(processing).processAsync(100L);
        verifyNoInteractions(analysis);
        assertThat(auditDetails()).contains("AI classification pending");
    }

    @Test
    void fullAiQueueLeavesTheDocumentPendingAndTheUploadSucceeds() {
        doThrow(new TaskRejectedException("queue full")).when(processing).processAsync(any());

        DocumentResponse uploaded = service.upload(file("midterm.pdf"), null, null, 10L, EMAIL);

        assertThat(uploaded.aiStatus()).isEqualTo(AiStatus.PENDING);
        assertThat(savedDocument().getAiStatus()).isEqualTo(AiStatus.PENDING);
    }

    @Test
    void withoutAiAnUnclassifiedUploadIsFailedOther() {
        when(ai.isConfigured()).thenReturn(false);

        DocumentResponse uploaded = service.upload(file("notes.pdf"), null, null, 10L, EMAIL);

        assertThat(uploaded.aiStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(uploaded.category()).isEqualTo(DocumentCategory.OTHER);
        verifyNoInteractions(classifier);
        verifyNoBackgroundJob();
    }

    @Test
    void manualSyllabusIsManualAndStillGetsTheDeadlineAnalysis() {
        DocumentResponse uploaded = service.upload(file("syllabus.pdf"), DocumentCategory.SYLLABUS, null, 10L, EMAIL);

        assertThat(uploaded.aiStatus()).isEqualTo(AiStatus.MANUAL);
        verify(analysis).analyzeAndCreateTasksAsync(100L);
        verifyNoInteractions(classifier);
        verifyNoBackgroundJob();
    }

    @Test
    void examAnswersAlreadyTeacherOnlyAreNotReportedAsForced() {
        service.upload(file("exam-key.pdf"), DocumentCategory.EXAM_ANSWERS, DocumentVisibility.TEACHER_ONLY, 10L, EMAIL);

        assertThat(savedDocument().getVisibility()).isEqualTo(DocumentVisibility.TEACHER_ONLY);
        assertThat(auditDetails()).doesNotContain("forced");
    }

    @Test
    void otherCategoriesKeepTheChosenVisibility() {
        DocumentResponse uploaded = service.upload(file("lecture1.pdf"), DocumentCategory.LECTURE,
                DocumentVisibility.COURSE_STUDENTS, 10L, EMAIL);

        assertThat(savedDocument().getVisibility()).isEqualTo(DocumentVisibility.COURSE_STUDENTS);
        assertThat(auditDetails()).doesNotContain("forced");
        // A manual category is not an AI result: nothing is classified, stored or reported.
        verifyNoInteractions(classifier);
        verifyNoBackgroundJob();
        assertThat(uploaded.aiCategory()).isNull();
        assertThat(uploaded.aiStatus()).isEqualTo(AiStatus.MANUAL);
    }

    @Test
    void storedContentTypeIsTheDetectedOneNotTheDeclaredOne() {
        service.upload(file("lecture.pdf"), DocumentCategory.LECTURE, null, 10L, EMAIL);

        assertThat(savedDocument().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void singleUploadWhoseContentDoesNotMatchTheExtensionIsRejected() {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.pdf", "application/pdf",
                "это просто текст".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(fake, DocumentCategory.LECTURE, null, 10L, EMAIL))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Содержимое файла не соответствует расширению");
                });
        verify(storage, never()).store(any(byte[].class));
    }

    @Test
    void multiUploadSkipsMismatchedFilesAndKeepsTheRest() {
        MockMultipartFile fake = new MockMultipartFile("files", "fake.pdf", "application/pdf",
                "это просто текст".getBytes(StandardCharsets.UTF_8));

        BatchUploadResponse response = service.uploadMultiple(List.of(file("real.pdf"), fake),
                DocumentCategory.LECTURE, null, 10L, EMAIL);

        assertThat(response.uploaded()).singleElement()
                .satisfies(uploaded -> assertThat(uploaded.document().originalFileName()).isEqualTo("real.pdf"));
        assertThat(response.skipped()).containsExactly(
                new SkippedFile("fake.pdf", "Содержимое файла не соответствует расширению"));
    }

    @Test
    void analysisOfExamAnswersIsRejected() {
        when(access.authorizeDocument(teacher, 100L, Permission.ANALYZE_DOCUMENT))
                .thenReturn(document(DocumentCategory.EXAM_ANSWERS));

        assertThatThrownBy(() -> service.requestAnalysis(100L, EMAIL))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Ответы к экзамену не анализируются на задания");
                });
        verifyNoInteractions(analysis);
    }

    @Test
    void examAnswersDoNotOfferAnalysis() {
        Document exam = document(DocumentCategory.EXAM_ANSWERS);
        when(access.authorizeDocument(teacher, 100L, Permission.VIEW_DOCUMENT)).thenReturn(exam);
        when(access.can(teacher, exam, Permission.ANALYZE_DOCUMENT)).thenReturn(true);

        assertThat(service.analysis(100L, EMAIL).canAnalyze()).isFalse();
    }

    private Document savedDocument() {
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documents).save(saved.capture());
        return saved.getValue();
    }

    private String auditDetails() {
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(EMAIL), eq(AuditService.DOCUMENT_UPLOADED), details.capture());
        return details.getValue();
    }

    private Document document(DocumentCategory category) {
        return Document.builder().id(100L).category(category).visibility(DocumentVisibility.TEACHER_ONLY)
                .textSnippet("text").course(course).owner(teacher).build();
    }

    /** A real one-page PDF: uploads are validated by content (FileTypeValidator). */
    /** Upload only asks the processing service whether OCR is needed (US-07); no classification or OCR job is queued. */
    private void verifyNoBackgroundJob() {
        verify(processing, never()).processAsync(any());
        verify(processing, never()).ocrAsync(any());
    }

    private static MockMultipartFile file(String name) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(out);
            // The declared type is deliberately wrong: the stored type must come from the content.
            return new MockMultipartFile("file", name, "text/html", out.toByteArray());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
