package com.university.docassistant.repository;

import com.university.docassistant.entity.DocumentClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** At most one row per document (document_id is unique). */
@Repository
public interface DocumentClassificationRepository extends JpaRepository<DocumentClassification, Long> {

    Optional<DocumentClassification> findByDocumentId(Long documentId);
}
