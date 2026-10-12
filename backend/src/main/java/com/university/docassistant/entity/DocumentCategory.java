package com.university.docassistant.entity;

/**
 * Types of course documents (US-05). The AI classifier chooses among the six Sprint 2 types (SYLLABUS, ASSIGNMENT,
 * LECTURE, EXAM_ANSWERS, REGULATION, OTHER); SYLLABUS and ASSIGNMENT documents also get their assignments and
 * deadlines extracted. EXAM_ANSWERS documents are always TEACHER_ONLY and never analyzed into assignments.
 * <p>
 * TRANSCRIPT and THESIS are legacy: existing rows keep them and still load, but the classifier no longer picks them
 * and the upload form no longer offers them.
 */
public enum DocumentCategory {
    SYLLABUS,
    ASSIGNMENT,
    LECTURE,
    EXAM_ANSWERS,
    REGULATION,
    /** Legacy: no longer chosen by the classifier. */
    TRANSCRIPT,
    /** Legacy: no longer chosen by the classifier. */
    THESIS,
    OTHER
}
