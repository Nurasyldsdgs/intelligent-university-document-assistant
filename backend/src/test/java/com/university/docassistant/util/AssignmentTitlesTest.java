package com.university.docassistant.util;

import com.university.docassistant.util.AssignmentTitles.WorkRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentTitlesTest {

    private static final LocalDateTime OCT_20 = LocalDateTime.of(2026, 10, 20, 18, 0);

    @ParameterizedTest
    @ValueSource(strings = {
            "Лабораторная работа 1", "Лабораторная работа №1", "Лабораторная работа № 1.", "лаб. работа №1",
            "Лаб. 1", "ЛАБ.№1", "лаб1", "Лаба 1", "Лабораторная  работа   1", "1 лабораторная работа",
            "Лабораторной работы №1"})
    void labVariantsAreTheSame(String title) {
        String normalized = AssignmentTitles.normalize(title);
        assertThat(AssignmentTitles.workRef(normalized)).contains(new WorkRef("лабораторная работа", 1));
    }

    @Test
    void sameWordingNormalizesEqual() {
        assertThat(AssignmentTitles.normalize("Лаб. работа №1: Сортировка!"))
                .isEqualTo(AssignmentTitles.normalize("лабораторная работа 1 — сортировка"))
                .isEqualTo("лабораторная работа 1 сортировка");
        assertThat(AssignmentTitles.normalize("Ёмкость стека")).isEqualTo("емкость стека");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Пр. 3", "Практическая работа №3", "Практика 3", "пр. работа 3", "Практическое занятие 3"})
    void practiceVariantsAreTheSame(String title) {
        assertThat(AssignmentTitles.normalize(title)).isEqualTo("практическая работа 3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"СРС 2", "С.Р.С. №2", "СРО-2", "Самостоятельная работа студента 2", "срс№2"})
    void srsVariantsAreTheSame(String title) {
        assertThat(AssignmentTitles.normalize(title)).isEqualTo("срс 2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"СРСП 2", "С.Р.С.П. 2", "СРОП №2", "Самостоятельная работа студента под руководством преподавателя 2"})
    void srspIsNotSrs(String title) {
        assertThat(AssignmentTitles.normalize(title)).isEqualTo("срсп 2");
    }

    @Test
    void differentNumbersAreNeverDuplicates() {
        assertThat(AssignmentTitles.likelySame("Лаб. 1", OCT_20, "Лаб. 2", OCT_20)).isFalse();
        assertThat(AssignmentTitles.likelySame("Лабораторная работа №1", null, "Лабораторная работа №12", null)).isFalse();
        assertThat(AssignmentTitles.likelySame("СРС 1", OCT_20, "СРС 2", OCT_20)).isFalse();
    }

    @Test
    void differentKindsWithTheSameNumberAreNotDuplicates() {
        assertThat(AssignmentTitles.likelySame("Лаб. 1", OCT_20, "Практика 1", OCT_20)).isFalse();
        assertThat(AssignmentTitles.likelySame("СРС 1", OCT_20, "СРСП 1", OCT_20)).isFalse();
    }

    @Test
    void sameNormalizedTitleIsADuplicateWhateverTheDeadline() {
        assertThat(AssignmentTitles.likelySame("Лаб. работа №1", null, "Лабораторная работа 1", OCT_20)).isTrue();
    }

    @Test
    void sameKindAndNumberWithDifferentWordingNeedsDeadlinesWithinADay() {
        String syllabus = "Лабораторная работа 1";
        String assignmentFile = "Лаб. 1: сортировка массивов";
        assertThat(AssignmentTitles.likelySame(syllabus, OCT_20, assignmentFile, OCT_20.plusHours(23))).isTrue();
        assertThat(AssignmentTitles.likelySame(syllabus, OCT_20, assignmentFile, OCT_20.minusDays(1))).isTrue();
        assertThat(AssignmentTitles.likelySame(syllabus, OCT_20, assignmentFile, OCT_20.plusDays(2))).isFalse();
        assertThat(AssignmentTitles.likelySame(syllabus, OCT_20, assignmentFile, null)).isFalse();
    }

    @Test
    void unrelatedTitlesAreNotDuplicatesAndEmptyTitlesNeverMatch() {
        assertThat(AssignmentTitles.likelySame("Эссе по истории", OCT_20, "Курсовой проект", OCT_20)).isFalse();
        assertThat(AssignmentTitles.likelySame("!!!", null, "???", null)).isFalse();
        assertThat(AssignmentTitles.normalize(null)).isEmpty();
    }

    @Test
    void wordsThatOnlyStartLikeAKindOfWorkAreKept() {
        assertThat(AssignmentTitles.normalize("Лабиринт и праздник")).isEqualTo("лабиринт и праздник");
    }
}
