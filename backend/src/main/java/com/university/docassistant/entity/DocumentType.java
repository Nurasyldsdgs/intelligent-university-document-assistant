package com.university.docassistant.entity;

/**
 * Coarse document type stored next to {@link DocumentCategory}. Set at upload from the category (see
 * {@link #fromCategory}); EXAM_ANSWERS and REGULATION have no category counterpart and are only set explicitly.
 */
public enum DocumentType {
    SYLLABUS,
    ASSIGNMENT,
    LECTURE,
    EXAM_ANSWERS,
    REGULATION,
    OTHER;

    public static DocumentType fromCategory(DocumentCategory category) {
        if (category == null) {
            return OTHER;
        }
        return switch (category) {
            case SYLLABUS -> SYLLABUS;
            case ASSIGNMENT -> ASSIGNMENT;
            case LECTURE -> LECTURE;
            case TRANSCRIPT, THESIS, OTHER -> OTHER;
        };
    }
}
