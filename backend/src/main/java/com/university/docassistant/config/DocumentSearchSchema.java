package com.university.docassistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Idempotent setup of PostgreSQL full-text search over documents.masked_text and document_chunks.content,
 * which ddl-auto cannot express:
 * a stored generated tsvector column and a GIN index on it. The column is not mapped in the entity, so Hibernate
 * never writes it; PostgreSQL recomputes it whenever masked_text changes.
 * The 'russian' configuration stems Russian words and, through english_stem, Latin-script words too.
 * Must stay in sync with the queries in DocumentRepository.searchVisibleIds and DocumentChunkRepository.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSearchSchema implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // US-09: search runs over the masked text, so an IIN or a phone number finds nothing. A column generated from
        // extracted_text by an older version is dropped (its index goes with it) and recreated over masked_text;
        // "ADD COLUMN IF NOT EXISTS" alone would keep the old definition, and SET EXPRESSION needs PostgreSQL 17+.
        List<String> current = jdbcTemplate.queryForList("""
                SELECT generation_expression FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'text_tsv'
                """, String.class);
        if (!current.isEmpty() && (current.get(0) == null || !current.get(0).contains("masked_text"))) {
            jdbcTemplate.execute("ALTER TABLE documents DROP COLUMN text_tsv");
            log.info("documents.text_tsv was built from extracted_text; rebuilding it from masked_text");
        }
        jdbcTemplate.execute("""
                ALTER TABLE documents ADD COLUMN IF NOT EXISTS text_tsv tsvector
                    GENERATED ALWAYS AS (to_tsvector('russian', coalesce(masked_text, ''))) STORED
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_documents_text_tsv ON documents USING GIN (text_tsv)");

        // The same for the Q&A chunks (DocumentChunkRepository).
        jdbcTemplate.execute("""
                ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS text_tsv tsvector
                    GENERATED ALWAYS AS (to_tsvector('russian', content)) STORED
                """);
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_document_chunks_text_tsv ON document_chunks USING GIN (text_tsv)");
        log.info("Full-text search columns and indexes on documents and document_chunks are in place");
    }
}
