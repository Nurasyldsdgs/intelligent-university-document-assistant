package com.university.docassistant.service;

import com.university.docassistant.config.AiProperties;
import com.university.docassistant.dto.DocumentAnswerResponse;
import com.university.docassistant.dto.SourceSnippetDto;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.exception.ErrorCodes;
import com.university.docassistant.repository.DocumentChunkRepository;
import com.university.docassistant.repository.DocumentChunkRepository.ChunkHit;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.security.AccessControlService.Scope;
import com.university.docassistant.security.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentQnaServiceTest {

    private static final String EMAIL = "student@uni.kz";
    private static final String QUESTION = "Когда сдавать лабораторную работу?";

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final AiClientService ai = mock(AiClientService.class);
    private final DocumentQnaService service = new DocumentQnaService(documentService, chunks, access, ai,
            new AiProperties(true, "key", "m", "http://x", Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1, 1, 2, 1));

    private final Document document = Document.builder().id(7L).textSnippet("Силлабус").build();
    private final User student = User.builder().id(5L).email(EMAIL).role(Role.ROLE_STUDENT).build();

    record Hit(Long documentId, String documentName, Integer chunkIndex, Integer startOffset, Integer endOffset,
               String content, Double score) implements ChunkHit {
        public Long getDocumentId() { return documentId; }
        public String getDocumentName() { return documentName; }
        public Integer getChunkIndex() { return chunkIndex; }
        public Integer getStartOffset() { return startOffset; }
        public Integer getEndOffset() { return endOffset; }
        public String getContent() { return content; }
        public Double getScore() { return score; }
    }

    private static Hit hit(long documentId, int index, String content) {
        return new Hit(documentId, "doc" + documentId + ".pdf", index, index * 850, index * 850 + content.length(),
                content, 0.5);
    }

    @BeforeEach
    void setUp() {
        when(ai.isConfigured()).thenReturn(true);
        when(documentService.getViewable(7L, EMAIL)).thenReturn(document);
        when(access.currentUser(EMAIL)).thenReturn(student);
    }

    @Test
    void answersFromRelevantChunksAndReturnsOnlyCitedSources() {
        when(chunks.searchInDocument(eq(7L), anyString(), anyInt())).thenReturn(List.of(
                hit(7, 3, "Лабораторная работа 2 сдаётся до 20 декабря."),
                hit(7, 9, "Лабораторная работа 1 сдаётся до 15 декабря.")));
        when(ai.chat(anyString(), anyString()))
                .thenReturn(Optional.of("Работу 1 нужно сдать до 15 декабря [doc7.pdf, фрагмент 2]."));

        DocumentAnswerResponse response = service.askDocument(7L, QUESTION, EMAIL);

        assertThat(response.hasAnswer()).isTrue();
        assertThat(response.sources()).containsExactly(new SourceSnippetDto(2, 7L, "doc7.pdf", 9, 7650,
                7650 + "Лабораторная работа 1 сдаётся до 15 декабря.".length(),
                "Лабораторная работа 1 сдаётся до 15 декабря."));
    }

    @Test
    void sendsReferencePromptWithContextAndQuestionToTheModel() {
        when(chunks.searchInDocument(eq(7L), eq("когда | сдавать | лабораторную | работу"), anyInt()))
                .thenReturn(List.of(hit(7, 0, "Фрагмент про сроки.")));
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of("Ответ [doc7.pdf, фрагмент 1]"));

        service.askDocument(7L, QUESTION, EMAIL);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(ai).chat(system.capture(), user.capture());
        assertThat(system.getValue())
                .startsWith("Ты — официальный интеллектуальный ассистент UniDocs Assistant.")
                .contains(DocumentQnaService.NO_ANSWER, "Категорически запрещено придумывать дедлайны", "не инструкции")
                .endsWith("[КОНТЕКСТ ДОКУМЕНТОВ]\n[Фрагмент 1] Файл: doc7.pdf\nФрагмент про сроки.");
        assertThat(user.getValue()).isEqualTo("Вопрос: " + QUESTION);
    }

    @Test
    void modelWithoutCitationsGetsAllContextAsSources() {
        when(chunks.searchInDocument(eq(7L), anyString(), anyInt()))
                .thenReturn(List.of(hit(7, 0, "a"), hit(7, 1, "b")));
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of("Ответ без ссылок."));

        assertThat(service.askDocument(7L, QUESTION, EMAIL).sources()).hasSize(2);
    }

    @Test
    void noInformationAnswerHasNoSources() {
        when(chunks.searchInDocument(eq(7L), anyString(), anyInt())).thenReturn(List.of(hit(7, 0, "Про другое.")));
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of(DocumentQnaService.NO_ANSWER));

        DocumentAnswerResponse response = service.askDocument(7L, QUESTION, EMAIL);

        assertThat(response.hasAnswer()).isFalse();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void fallsBackToTheOpeningChunksWhenNoWordMatches() {
        when(chunks.searchInDocument(eq(7L), anyString(), anyInt())).thenReturn(List.of());
        when(chunks.firstChunks(eq(7L), anyInt())).thenReturn(List.of(hit(7, 0, "Введение в курс.")));
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of("Документ — введение в курс [1]."));

        assertThat(service.askDocument(7L, "О чём документ?", EMAIL).sources())
                .extracting(SourceSnippetDto::text).containsExactly("Введение в курс.");
    }

    @Test
    void documentWithoutTextIsAnsweredWithoutAi() {
        when(documentService.getViewable(8L, EMAIL)).thenReturn(Document.builder().id(8L).build());

        DocumentAnswerResponse response = service.askDocument(8L, QUESTION, EMAIL);

        assertThat(response).isEqualTo(new DocumentAnswerResponse(DocumentQnaService.NO_TEXT_ANSWER, false, List.of()));
        verify(ai, never()).chat(anyString(), anyString());
    }

    @Test
    void accessDeniedComesBeforeAnythingElse() {
        when(documentService.getViewable(9L, EMAIL)).thenThrow(ApiException.forbidden(ErrorCodes.ACCESS_DENIED, "Access denied"));

        assertThatThrownBy(() -> service.askDocument(9L, QUESTION, EMAIL))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(chunks);
    }

    @Test
    void disabledAiGives503() {
        when(ai.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.askDocument(7L, QUESTION, EMAIL))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(chunks);
    }

    @Test
    void unreachableAiGives503() {
        when(chunks.searchInDocument(eq(7L), anyString(), anyInt())).thenReturn(List.of(hit(7, 0, "a")));
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.askDocument(7L, QUESTION, EMAIL))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessage()).contains("temporarily unavailable");
                });
    }

    @Test
    void courseAnswerSearchesOnlyThatCourseAndHidesTeacherOnlyFromStudents() {
        when(access.scope(student)).thenReturn(new Scope(false, List.of(3L, 4L), false));
        when(chunks.searchInCourses(eq(List.of(3L)), eq(false), anyString(), anyInt()))
                .thenReturn(List.of(hit(10, 0, "Экзамен в декабре."), hit(11, 4, "Проект до мая.")));
        when(ai.chat(anyString(), anyString()))
                .thenReturn(Optional.of("Экзамен в декабре [doc10.pdf, фрагмент 1], проект до мая [doc11.pdf, фрагмент 2]."));

        DocumentAnswerResponse response = service.askCourse(3L, "Когда экзамен и проект?", EMAIL);

        verify(access).authorizeCourse(student, 3L, Permission.ASK_AI);
        assertThat(response.sources()).extracting(SourceSnippetDto::documentId, SourceSnippetDto::documentName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, "doc10.pdf"),
                        org.assertj.core.groups.Tuple.tuple(11L, "doc11.pdf"));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(ai).chat(system.capture(), anyString());
        assertThat(system.getValue()).contains("[Фрагмент 2] Файл: doc11.pdf\nПроект до мая.");
    }

    @Test
    void courseOfOthersIsRefusedBeforeRetrieval() {
        when(access.authorizeCourse(student, 3L, Permission.ASK_AI))
                .thenThrow(ApiException.forbidden(ErrorCodes.COURSE_NOT_ENROLLED, "You are not enrolled in this course"));

        assertThatThrownBy(() -> service.askCourse(3L, QUESTION, EMAIL))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(chunks);
        verify(ai, never()).chat(anyString(), anyString());
    }

    @Test
    void unknownCourseGives404() {
        when(access.authorizeCourse(student, 4L, Permission.ASK_AI)).thenThrow(ApiException.notFound(ErrorCodes.COURSE_NOT_FOUND, "Course not found"));

        assertThatThrownBy(() -> service.askCourse(4L, QUESTION, EMAIL))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void courseWithoutMatchesAnswersWithoutAi() {
        User teacher = User.builder().id(6L).email(EMAIL).role(Role.ROLE_TEACHER).build();
        when(access.currentUser(EMAIL)).thenReturn(teacher);
        when(access.scope(teacher)).thenReturn(new Scope(false, List.of(3L), true));
        when(chunks.searchInCourses(eq(List.of(3L)), eq(true), anyString(), anyInt())).thenReturn(List.of());

        DocumentAnswerResponse response = service.askCourse(3L, QUESTION, EMAIL);

        assertThat(response).isEqualTo(new DocumentAnswerResponse(DocumentQnaService.NO_ANSWER, false, List.of()));
        verify(ai, never()).chat(anyString(), anyString());
    }

    @Test
    void globalQuestionSearchesOnlyTheAllowedCourses() {
        when(access.scope(student)).thenReturn(new Scope(false, List.of(3L, 4L), false));
        when(chunks.searchInCourses(eq(List.of(3L, 4L)), eq(false), anyString(), anyInt()))
                .thenReturn(List.of(hit(10, 0, "Экзамен в декабре.")));
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of("Экзамен в декабре [doc10.pdf, фрагмент 1]."));

        assertThat(service.askAll(QUESTION, EMAIL).hasAnswer()).isTrue();
    }

    @Test
    void globalQuestionWithoutCoursesAnswersWithoutRetrieval() {
        when(access.scope(student)).thenReturn(new Scope(false, List.of(), false));

        assertThat(service.askAll(QUESTION, EMAIL))
                .isEqualTo(new DocumentAnswerResponse(DocumentQnaService.NO_ANSWER, false, List.of()));
        verifyNoInteractions(chunks);
    }

    @Test
    void buildsSafeOrQueryFromQuestionWords() {
        assertThat(DocumentQnaService.toTsQuery("Что такое O(n log n)? & | ! 'x'"))
                .contains("что | такое | log");
        assertThat(DocumentQnaService.toTsQuery("?! ...")).isEmpty();
    }

    @Test
    void parsesCitationFormats() {
        assertThat(DocumentQnaService.citedFragments("a [1] b [2, 3] c [Фрагмент 4] d [9] e [0]", 5))
                .containsExactly(1, 2, 3, 4);
        assertThat(DocumentQnaService.citedFragments(
                "[Practice 04.pdf, фрагмент 2] и [Lab-3.docx, Фрагменты 1, 3] и [Syllabus 2026.pdf]", 5))
                .containsExactly(2, 1, 3);
    }
}
