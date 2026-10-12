package com.university.docassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A piece of a document's extracted text, the unit of retrieval for Q&A. Offsets point into
 * {@code documents.extracted_text}. Rows are written in batches by DocumentChunkService and searched through the
 * generated column document_chunks.text_tsv (see DocumentSearchSchema); the database removes them together with
 * their document.
 */
@Entity
@Table(name = "document_chunks",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_chunks_document_index",
                columnNames = {"document_id", "chunk_index"}),
        indexes = @Index(name = "idx_document_chunks_document_id", columnList = "document_id"))
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
