package com.university.docassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.docassistant.config.AiProperties;
import com.university.docassistant.dto.TaskExtractDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiTaskExtractionServiceTest {

    private static final String LONG_TEXT = "Лабораторная работа 1: реализовать сортировку слиянием. Сдать до 15 декабря.";

    private final AiClientService client = mock(AiClientService.class);
    private final AiProperties properties = new AiProperties(true, "key", "model", "http://localhost",
            Duration.ofSeconds(1), Duration.ofSeconds(1), 1_000, 1, 1, 1);
    private final AiTaskExtractionService service = new AiTaskExtractionService(client, properties, new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneOffset.UTC));

    @Test
    void parsesPlainArray() {
        List<TaskExtractDto> tasks = service.parseTasks("""
                [{"title": "Лаба 1", "description": "Сортировка", "deadline": "2026-12-15T23:59:00"},
                 {"title": "Проект", "description": null, "deadline": null}]""");

        assertThat(tasks).containsExactly(
                new TaskExtractDto("Лаба 1", "Сортировка", LocalDateTime.of(2026, 12, 15, 23, 59)),
                new TaskExtractDto("Проект", null, null));
    }

    @Test
    void toleratesFencesCommentsTrailingCommasAndProse() {
        List<TaskExtractDto> tasks = service.parseTasks("""
                Вот результат:
                ```json
                [
                  {
                    "title": "Лаба 2", // комментарий модели
                    "deadline": "2026-11-01",
                  },
                ]
                ```""");

        assertThat(tasks).containsExactly(new TaskExtractDto("Лаба 2", null, LocalDateTime.of(2026, 11, 1, 23, 59)));
    }

    @Test
    void acceptsArrayWrappedInObject() {
        assertThat(service.parseTasks("{\"tasks\": [{\"title\": \"Эссе\"}]}"))
                .extracting(TaskExtractDto::title).containsExactly("Эссе");
    }

    @Test
    void dropsInvalidItemsAndDuplicates() {
        List<TaskExtractDto> tasks = service.parseTasks("""
                [{"description": "без названия"}, "строка", {"title": "  "},
                 {"title": "Лаба 3", "deadline": "когда-нибудь"}, {"title": "лаба 3", "deadline": null}]""");

        assertThat(tasks).containsExactly(new TaskExtractDto("Лаба 3", null, null));
    }

    @Test
    void unusableAnswerYieldsNoTasks() {
        assertThat(service.parseTasks("Извините, я не нашёл заданий.")).isEmpty();
        assertThat(service.parseTasks("{\"error\": \"oops\"}")).isEmpty();
    }

    @Test
    void parsesDeadlineFormats() {
        assertThat(AiTaskExtractionService.parseDeadline("2026-12-15T10:30")).isEqualTo(LocalDateTime.of(2026, 12, 15, 10, 30));
        assertThat(AiTaskExtractionService.parseDeadline("2026-12-15 10:30:00")).isEqualTo(LocalDateTime.of(2026, 12, 15, 10, 30));
        assertThat(AiTaskExtractionService.parseDeadline("2026-12-15T10:30:00Z")).isEqualTo(LocalDateTime.of(2026, 12, 15, 10, 30));
        assertThat(AiTaskExtractionService.parseDeadline("null")).isNull();
        assertThat(AiTaskExtractionService.parseDeadline("15.12.2026")).isNull();
    }

    @Test
    void shortTextIsNotSentToAi() {
        assertThat(service.extractTasksFromText("Короткий текст")).isEmpty();
        assertThat(service.extractTasksFromText(null)).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void sendsTodayAndCutsLongTextToTheConfiguredLimit() {
        when(client.chat(anyString(), anyString())).thenReturn(Optional.of("[{\"title\": \"Лаба 1\"}]"));
        String text = LONG_TEXT + "x".repeat(5_000);

        List<TaskExtractDto> tasks = service.extractTasksFromText(text);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(client).chat(eq(AiTaskExtractionService.SYSTEM_PROMPT), message.capture());
        assertThat(message.getValue()).startsWith("Сегодняшняя дата: 2026-10-03").contains(LONG_TEXT)
                .doesNotContain(text.substring(0, 1_001));
        assertThat(tasks).extracting(TaskExtractDto::title).containsExactly("Лаба 1");
    }

    @Test
    void unavailableAiYieldsNoTasks() {
        when(client.chat(anyString(), anyString())).thenReturn(Optional.empty());

        assertThat(service.extractTasksFromText(LONG_TEXT)).isEmpty();
        assertThat(service.analyze(LONG_TEXT)).isEmpty();
    }

    @Test
    void parsesSummaryTopicsAndTasks() {
        Optional<AiTaskExtractionService.Analysis> analysis = service.parseAnalysis("""
                Вот анализ: {"summary": "Силлабус курса алгоритмов.",
                 "topics": ["Сортировки", "Графы\\nи деревья", "Сортировки", "", null, 5],
                 "tasks": [{"title": "Лаба 1", "deadline": "2026-12-15"}]}""");

        assertThat(analysis).hasValueSatisfying(result -> {
            assertThat(result.summary()).isEqualTo("Силлабус курса алгоритмов.");
            assertThat(result.topics()).containsExactly("Сортировки", "Графы и деревья", "5");
            assertThat(result.tasks()).containsExactly(
                    new TaskExtractDto("Лаба 1", null, LocalDateTime.of(2026, 12, 15, 23, 59)));
        });
    }

    @Test
    void successfulAnalysisWithoutTasksIsNotAFailure() {
        assertThat(service.parseAnalysis("{\"summary\": \"Положение об общежитии.\", \"topics\": [], \"tasks\": []}"))
                .hasValueSatisfying(result -> {
                    assertThat(result.summary()).isEqualTo("Положение об общежитии.");
                    assertThat(result.tasks()).isEmpty();
                });
    }

    @Test
    void bareTaskArrayIsStillAccepted() {
        assertThat(service.parseAnalysis("[{\"title\": \"Эссе\"}]"))
                .hasValueSatisfying(result -> {
                    assertThat(result.summary()).isNull();
                    assertThat(result.topics()).isEmpty();
                    assertThat(result.tasks()).extracting(TaskExtractDto::title).containsExactly("Эссе");
                });
    }
}
