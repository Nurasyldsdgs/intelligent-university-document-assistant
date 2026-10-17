package com.university.docassistant.config;

import com.university.docassistant.service.DocumentChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chunks documents that have extracted text but no chunks yet: those uploaded before Q&A existed, or whose
 * chunking failed at upload. Idempotent; processes small batches so texts are never all in memory at once.
 * Chunks are cut from documents.masked_text (US-09), so personal data never reaches Q&A; a document not masked yet
 * is skipped here and chunked by DocumentPrivacyBackfill.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChunkBackfill implements ApplicationRunner {

    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final DocumentChunkService chunkService;

    @Override
    public void run(ApplicationArguments args) {
        int documents = 0;
        long lastId = 0;
        while (true) {
            List<Long> ids = jdbcTemplate.queryForList("""
                    SELECT d.id FROM documents d
                    WHERE d.id > ? AND d.masked_text IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM document_chunks ch WHERE ch.document_id = d.id)
                    ORDER BY d.id LIMIT ?
                    """, Long.class, lastId, BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                String text = jdbcTemplate.queryForObject(
                        "SELECT masked_text FROM documents WHERE id = ?", String.class, id);
                chunkService.index(id, text);
                documents++;
            }
            lastId = ids.get(ids.size() - 1);
        }
        if (documents > 0) {
            log.info("Chunked {} existing document(s) for Q&A", documents);
        }
    }
}
