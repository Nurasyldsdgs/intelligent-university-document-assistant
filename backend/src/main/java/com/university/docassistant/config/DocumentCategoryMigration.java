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
 * alters an existing constraint, so without this a category added later (OTHER) would be rejected by the database.
 * Documents of retired types (REGULATIONS, EXAM_ANSWERS, GRANT_PROPOSAL) become OTHER first, otherwise they could
 * neither satisfy the new constraint nor be loaded into the enum.
 * Idempotent: the constraint is rebuilt from the enum at every start.
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
                WHERE type = 'OTHER' AND category IN ('SYLLABUS', 'ASSIGNMENT', 'LECTURE')
                """);
        if (typed > 0) {
            log.info("Set documents.type from category for {} document(s)", typed);
        }
    }
}
