package com.university.docassistant.dto;

import com.university.docassistant.entity.AssignmentStatus;
import com.university.docassistant.entity.TaskStatus;

import java.time.LocalDateTime;

/**
 * A task with the names of its document and course. Built directly by the JPQL constructor expressions in
 * TaskRepository, so listing tasks never loads a document's extracted text.
 */
public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDateTime deadline,
        TaskStatus status,
        Long documentId,
        String documentName,
        Long courseId,
        String courseTitle,
        String courseCode,
        Long assignmentId,
        /** CLOSED: the assignment takes no more submissions; null for tasks without an assignment. */
        AssignmentStatus assignmentStatus,
        /** Files the student has handed in for this task. */
        Long submissionCount
) {
    /** Select list matching the canonical constructor; the queries alias Task as t, document as d, course as c. */
    public static final String SELECT = """
            SELECT new com.university.docassistant.dto.TaskResponse(
                t.id, t.title, t.description, t.deadline, t.status,
                d.id, d.originalFileName, c.id, c.title, c.code, a.id, a.status,
                (SELECT COUNT(s) FROM TaskSubmission s WHERE s.task.id = t.id))
            FROM Task t LEFT JOIN t.document d LEFT JOIN t.course c LEFT JOIN t.assignment a
            """;
}
