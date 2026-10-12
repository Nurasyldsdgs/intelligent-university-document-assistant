package com.university.docassistant.service;

import com.university.docassistant.service.DocumentChunker.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes a document's chunks for Q&A retrieval. A 100 000-character text gives ~120 chunks, so rows are inserted as
 * one JDBC batch rather than entity by entity (IDENTITY ids would turn saveAll into ~120 round trips).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkService {

    private final DocumentChunker chunker;
    private final JdbcTemplate jdbcTemplate;

    /** Replaces the document's chunks with ones cut from {@code text}; returns their number. */
    @Transactional
    public int index(Long documentId, String text) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
        List<Chunk> chunks = chunker.split(text);
        if (chunks.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO document_chunks (document_id, chunk_index, start_offset, end_offset, content)
                        VALUES (?, ?, ?, ?, ?)
                        """, chunks, 200, (statement, chunk) -> {
                    statement.setLong(1, documentId);
                    statement.setInt(2, chunk.index());
                    statement.setInt(3, chunk.startOffset());
                    statement.setInt(4, chunk.endOffset());
                    statement.setString(5, chunk.content());
                });
        log.debug("Indexed document {} as {} chunk(s)", documentId, chunks.size());
        return chunks.size();
    }
}
