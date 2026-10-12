package com.university.docassistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes documents.course_id mandatory (FK -> courses.id, NOT NULL). ddl-auto=update never tightens an existing
 * column, so this runs at startup. Documents uploaded before every document had a course are moved into one archive
 * course without a teacher or students, which keeps their old rule: reachable by ADMIN only.
 * Idempotent: once there are no such documents, only the (no-op) constraint statements run.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DocumentCourseMigration implements ApplicationRunner {

    static final String ARCHIVE_COURSE_CODE = "ARCHIVE-NO-COURSE";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE course_id IS NULL", Integer.class);
        if (orphans != null && orphans > 0) {
            Long archiveId = jdbcTemplate.query("SELECT id FROM courses WHERE code = ? ORDER BY id LIMIT 1",
                    rs -> rs.next() ? rs.getLong(1) : null, ARCHIVE_COURSE_CODE);
            if (archiveId == null) {
                archiveId = jdbcTemplate.queryForObject("""
                        INSERT INTO courses (title, code, description)
                        VALUES ('Архив: документы без курса', ?,
                                'Создан автоматически для документов, загруженных до обязательной привязки к курсу.')
                        RETURNING id
                        """, Long.class, ARCHIVE_COURSE_CODE);
            }
            jdbcTemplate.update("UPDATE documents SET course_id = ? WHERE course_id IS NULL", archiveId);
            log.info("Moved {} document(s) without a course into archive course {}", orphans, archiveId);
        }

        jdbcTemplate.execute("ALTER TABLE documents ALTER COLUMN course_id SET NOT NULL");
        // Hibernate adds the FK when it creates the column; an older hand-made schema may lack it.
        Boolean hasForeignKey = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_constraint con
                        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
                    WHERE con.contype = 'f' AND con.conrelid = 'documents'::regclass
                      AND con.confrelid = 'courses'::regclass AND att.attname = 'course_id')
                """, Boolean.class);
        if (!Boolean.TRUE.equals(hasForeignKey)) {
            jdbcTemplate.execute("""
                    ALTER TABLE documents ADD CONSTRAINT fk_documents_course
                        FOREIGN KEY (course_id) REFERENCES courses (id)
                    """);
            log.info("Added foreign key documents.course_id -> courses.id");
        }
    }
}
