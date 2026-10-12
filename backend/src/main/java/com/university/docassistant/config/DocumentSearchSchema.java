package com.university.docassistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent setup of PostgreSQL full-text search over documents.extracted_text and document_chunks.content,
 * which ddl-auto cannot express:
 * a stored generated tsvector column and a GIN index on it. The column is not mapped in the entity, so Hibernate
 * never writes it; PostgreSQL recomputes it whenever extracted_text changes.
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
        jdbcTemplate.execute("""
                ALTER TABLE documents ADD COLUMN IF NOT EXISTS text_tsv tsvector
                    GENERATED ALWAYS AS (to_tsvector('russian', coalesce(extracted_text, ''))) STORED
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
