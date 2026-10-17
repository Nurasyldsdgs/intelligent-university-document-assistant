package com.university.docassistant.service;

import com.university.docassistant.config.PrivacyProperties;
import com.university.docassistant.entity.AiStatus;
import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentType;
import com.university.docassistant.entity.DocumentVisibility;
import com.university.docassistant.entity.OcrStatus;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.exception.ErrorCodes;
import com.university.docassistant.repository.AssignmentRepository;
import com.university.docassistant.repository.BatchImportRepository;
import com.university.docassistant.repository.CourseRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.EnrollmentRepository;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.repository.UserRepository;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.service.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-07 step 2: the teacher's two ways out of "Нужна проверка" - set the type by hand, or run the recognition again.
 * With the real AccessControlService, so the 403s are the real decisions.
 */
class DocumentOcrReviewTest {

    private static final long ID = 50L;

    private final UserRepository users = mock(UserRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AiClientService ai = mock(AiClientService.class);
    private final DocumentProcessingService processing = mock(DocumentProcessingService.class);
    private final DocumentAnalysisService analysis = mock(DocumentAnalysisService.class);
    private final DocumentClassificationService classifier = mock(DocumentClassificationService.class);
    private final AccessControlService access = new AccessControlService(users, courses, documents, enrollments, audit);
    private final DocumentService service = new DocumentService(documents, mock(TaskRepository.class),
            mock(AssignmentRepository.class), access, mock(StorageService.class), mock(DocumentParserService.class),
            analysis, ai, mock(DocumentChunkService.class), audit, classifier, processing,
            mock(BatchImportRepository.class), mock(AssignmentService.class),
            new DocumentPrivacyService(new PrivacyProperties(true, List.of())));

    private final User teacher = User.builder().id(2L).email("teacher@uni.kz").role(Role.ROLE_TEACHER).build();
    private final User otherTeacher = User.builder().id(3L).email("other@uni.kz").role(Role.ROLE_TEACHER).build();
    private final User student = User.builder().id(1L).email("student@uni.kz").role(Role.ROLE_STUDENT).build();
    private final Course course = Course.builder().id(10L).title("Algorithms").teacher(teacher).build();
    private Document scan;

    @BeforeEach
    void setUp() {
        for (User user : List.of(teacher, otherTeacher, student)) {
            when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
        when(enrollments.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(true);
        // A scan whose recognition failed: the AI classified it from the file name only.
        scan = Document.builder().id(ID).originalFileName("scan.pdf").storedFileName("s").contentType("application/pdf")
                .fileSize(1L).category(DocumentCategory.OTHER).visibility(DocumentVisibility.COURSE_STUDENTS)
                .aiStatus(AiStatus.FAILED).ocrStatus(OcrStatus.FAILED).course(course).owner(teacher)
                .uploadedAt(LocalDateTime.now()).build();
        when(documents.findWithOwnerById(ID)).thenReturn(Optional.of(scan));
        when(documents.changeOcrStatus(ID, OcrStatus.FAILED, OcrStatus.PENDING)).thenReturn(1);
        when(processing.ocrAvailable()).thenReturn(true);
        when(ai.isConfigured()).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** What the real transaction does after commit. */
    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void assertStatus(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(status);
            if (code != null) {
                assertThat(ex.getCode()).isEqualTo(code);
            }
        });
    }

    @Nested
    class SetCategoryByHand {

        @Test
        void teacherSetsTheTypeTheAiCouldNotFind() {
            service.setCategory(ID, DocumentCategory.LECTURE, "teacher@uni.kz");
            commit();

            verify(documents).updateClassification(ID, DocumentCategory.LECTURE, DocumentType.LECTURE, AiStatus.MANUAL);
            verify(classifier).clearResult(ID); // The old AI result no longer matches.
            verify(audit).record(eq("teacher@uni.kz"), eq(AuditService.DOCUMENT_CATEGORY_SET), contains("LECTURE"));
            verify(analysis, never()).analyzeAndCreateTasksAsync(any());
        }

        @Test
        void examAnswersByHandAreHiddenFromStudentsAndNeverAnalyzed() {
            scan.setMaskedText("Ответы к экзамену ...");
            when(documents.forceTeacherOnly(ID)).thenReturn(1);

            service.setCategory(ID, DocumentCategory.EXAM_ANSWERS, "teacher@uni.kz");
            commit();

            verify(documents).forceTeacherOnly(ID);
            verify(analysis, never()).analyzeAndCreateTasksAsync(any());
        }

        @Test
        void syllabusWithTextGetsItsDeadlineAnalysisAfterCommit() {
            scan.setMaskedText("Силлабус: лабораторная работа 1 до 20.10.2026");

            service.setCategory(ID, DocumentCategory.SYLLABUS, "teacher@uni.kz");
            verify(analysis, never()).analyzeAndCreateTasksAsync(any());
            commit();

            verify(analysis).analyzeAndCreateTasksAsync(ID);
        }

        @Test
        void notWhileTheAiOrOcrStillWorks() {
            scan.setAiStatus(AiStatus.PROCESSING);
            assertStatus(() -> service.setCategory(ID, DocumentCategory.LECTURE, "teacher@uni.kz"),
                    HttpStatus.CONFLICT, ErrorCodes.DOCUMENT_BEING_PROCESSED);

            scan.setAiStatus(AiStatus.MANUAL);
            scan.setOcrStatus(OcrStatus.RUNNING);
            assertStatus(() -> service.setCategory(ID, DocumentCategory.LECTURE, "teacher@uni.kz"),
                    HttpStatus.CONFLICT, ErrorCodes.DOCUMENT_BEING_PROCESSED);
            verify(documents, never()).updateClassification(anyLong(), any(), any(), any());
        }

        @Test
        void studentAndTeacherOfAnotherCourseGet403() {
            assertStatus(() -> service.setCategory(ID, DocumentCategory.LECTURE, "student@uni.kz"), HttpStatus.FORBIDDEN, null);
            assertStatus(() -> service.setCategory(ID, DocumentCategory.LECTURE, "other@uni.kz"), HttpStatus.FORBIDDEN, null);
            verify(documents, never()).updateClassification(anyLong(), any(), any(), any());
        }
    }

    @Nested
    class RetryRecognition {

        @Test
        void aiChosenTypeIsChosenAgainAfterTheNewOcr() {
            service.retryOcr(ID, "teacher@uni.kz");
            verify(processing, never()).processAsync(any()); // Not before the commit.
            commit();

            verify(documents).changeOcrStatus(ID, OcrStatus.FAILED, OcrStatus.PENDING);
            verify(documents).setAiStatus(ID, AiStatus.PENDING);
            verify(processing).processAsync(ID); // The job runs OCR first, then classifies.
            verify(processing, never()).ocrAsync(any());
            verify(audit).record(eq("teacher@uni.kz"), eq(AuditService.OCR_RETRY_REQUESTED), contains("classified again"));
        }

        @Test
        void typeChosenByHandStaysAndOnlyTheOcrRunsAgain() {
            scan.setAiStatus(AiStatus.MANUAL);

            service.retryOcr(ID, "teacher@uni.kz");
            commit();

            verify(documents, never()).setAiStatus(anyLong(), any());
            verify(processing).ocrAsync(ID);
            verify(processing, never()).processAsync(any());
        }

        @Test
        void onlyAFailedRecognitionCanBeRetried() {
            when(documents.changeOcrStatus(ID, OcrStatus.FAILED, OcrStatus.PENDING)).thenReturn(0);

            assertStatus(() -> service.retryOcr(ID, "teacher@uni.kz"), HttpStatus.CONFLICT, ErrorCodes.OCR_NOT_FAILED);
            verify(processing, never()).processAsync(any());
        }

        @Test
        void withoutOcrOnTheServerTheRetryIs503() {
            when(processing.ocrAvailable()).thenReturn(false);

            assertStatus(() -> service.retryOcr(ID, "teacher@uni.kz"), HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCodes.OCR_UNAVAILABLE);
            verify(documents, never()).changeOcrStatus(anyLong(), any(), any());
        }

        @Test
        void studentCannotRetry() {
            assertStatus(() -> service.retryOcr(ID, "student@uni.kz"), HttpStatus.FORBIDDEN, null);
            verify(documents, never()).changeOcrStatus(anyLong(), any(), any());
            verify(audit, never()).record(anyString(), eq(AuditService.OCR_RETRY_REQUESTED), anyString());
        }
    }
}
