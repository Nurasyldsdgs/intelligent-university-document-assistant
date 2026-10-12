package com.university.docassistant.entity;

/**
 * Who in the document's course can see it: its students too, or only its teacher (and ADMIN).
 * PUBLIC_ALL is kept so older rows still load; it now means the same as COURSE_STUDENTS and new uploads cannot use it.
 */
public enum DocumentVisibility {
    PUBLIC_ALL,
    COURSE_STUDENTS,
    TEACHER_ONLY
}
