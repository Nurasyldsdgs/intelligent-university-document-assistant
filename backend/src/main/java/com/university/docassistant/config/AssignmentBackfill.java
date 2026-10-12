        package com.university.docassistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves data to the Document -> Assignment -> student Task model at startup. Tasks the AI used to create for the
 * owner of a course document become assignments of that course, and every enrolled student is then given the
 * tasks they are missing. Idempotent: once converted, the first two statements find nothing, and the last one only
 * repairs gaps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int assignments = jdbcTemplate.update("""
                INSERT INTO assignments (title, description, deadline, status, course_id, source_document_id, created_at)
                SELECT DISTINCT ON (t.document_id, lower(t.title), t.deadline)
                       t.title, t.description, t.deadline, 'ACTIVE', d.course_id, t.document_id, now()
                FROM tasks t JOIN documents d ON d.id = t.document_id
                WHERE t.assignment_id IS NULL AND d.course_id IS NOT NULL
                ORDER BY t.document_id, lower(t.title), t.deadline, t.id
                """);
        int legacyTasks = jdbcTemplate.update("""
                DELETE FROM tasks t USING documents d
                WHERE t.document_id = d.id AND t.assignment_id IS NULL AND d.course_id IS NOT NULL
                """);
        int studentTasks = jdbcTemplate.update("""
                INSERT INTO tasks (title, description, deadline, status, document_id, course_id, user_id, assignment_id)
                SELECT a.title, a.description, a.deadline, 'PENDING', a.source_document_id, a.course_id,
                       e.student_id, a.id
                FROM assignments a JOIN course_students e ON e.course_id = a.course_id
                WHERE a.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.assignment_id = a.id AND t.user_id = e.student_id)
                """);
        if (assignments + legacyTasks + studentTasks > 0) {
            log.info("Assignments backfill: {} assignment(s) from {} legacy task(s), {} student task(s) handed out",
                    assignments, legacyTasks, studentTasks);
        }
    }
}
