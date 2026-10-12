package com.university.docassistant.entity;

/**
 * Types of course documents (US-05). The AI classifier picks one of these; SYLLABUS and ASSIGNMENT documents also
 * get their assignments and deadlines extracted. Rows with retired types are moved to OTHER by DocumentCategoryMigration.
 */
public enum DocumentCategory {
    SYLLABUS,
    ASSIGNMENT,
    LECTURE,
    TRANSCRIPT,
    THESIS,
    OTHER
}
