package com.university.docassistant.entity;

/**
 * Document type stored next to {@link DocumentCategory}, with the six Sprint 2 values. Set at upload from the
 * category (see {@link #fromCategory}): each spec category maps to the type of the same name, and the legacy
 * categories TRANSCRIPT and THESIS map to OTHER.
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
            case EXAM_ANSWERS -> EXAM_ANSWERS;
            case REGULATION -> REGULATION;
            case TRANSCRIPT, THESIS, OTHER -> OTHER;
        };
    }
}
