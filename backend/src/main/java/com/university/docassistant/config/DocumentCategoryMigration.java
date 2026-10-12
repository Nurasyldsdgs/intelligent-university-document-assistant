package com.university.docassistant.config;

import com.university.docassistant.entity.DocumentCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Keeps the documents.category check constraint in step with {@link DocumentCategory}. ddl-auto=update never
 * alters an existing constraint, so without this a category added later (EXAM_ANSWERS, REGULATION) would be
 * rejected by the database. At every start, in order:
 * <ol>
 *   <li>rows of the old plural name REGULATIONS become REGULATION;</li>
 *   <li>rows of categories no longer in the enum (e.g. GRANT_PROPOSAL) become OTHER, otherwise they could neither
 *       satisfy the new constraint nor be loaded into the enum;</li>
 *   <li>the constraint is rebuilt from the enum;</li>
 *   <li>documents.type is backfilled from the category where a type of the same name exists;</li>
 *   <li>EXAM_ANSWERS documents are forced to TEACHER_ONLY, the same rule DocumentService applies on upload.</li>
 * </ol>
 * Idempotent: once applied, the updates find nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCategoryMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String values = Arrays.stream(DocumentCategory.values())
                .map(category -> "'" + category.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_category_check");
        int renamed = jdbcTemplate.update("UPDATE documents SET category = 'REGULATION' WHERE category = 'REGULATIONS'");
        if (renamed > 0) {
            log.info("Renamed category REGULATIONS to REGULATION for {} document(s)", renamed);
        }
        int retired = jdbcTemplate.update("UPDATE documents SET category = 'OTHER' WHERE category NOT IN ("
                + values + ")");
        if (retired > 0) {
            log.info("Moved {} document(s) of retired categories to OTHER", retired);
        }
        jdbcTemplate.execute("ALTER TABLE documents ADD CONSTRAINT documents_category_check CHECK (category IN ("
                + values + "))");
        // documents.type came later with default OTHER; rows whose category has a matching type get it.
        int typed = jdbcTemplate.update("""
                UPDATE documents SET type = category
                WHERE type = 'OTHER' AND category IN ('SYLLABUS', 'ASSIGNMENT', 'LECTURE', 'EXAM_ANSWERS', 'REGULATION')
                """);
        if (typed > 0) {
            log.info("Set documents.type from category for {} document(s)", typed);
        }
        // Exam answers must never reach students, whatever visibility they were uploaded with.
        int hidden = jdbcTemplate.update("""
                UPDATE documents SET visibility = 'TEACHER_ONLY'
                WHERE category = 'EXAM_ANSWERS' AND visibility <> 'TEACHER_ONLY'
                """);
        if (hidden > 0) {
            log.warn("Forced {} EXAM_ANSWERS document(s) to TEACHER_ONLY", hidden);
        }
    }
}
