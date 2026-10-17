package com.university.docassistant.repository;

import com.university.docassistant.entity.AiStatus;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentType;
import com.university.docassistant.entity.DocumentVisibility;
import com.university.docassistant.entity.OcrStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lists return {@link DocumentSummary} rows, so a page never loads the extracted text of its documents.
 * The JPQL text blocks are compile-time constants built from {@link DocumentSummary#SELECT}.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** For ADMIN: every document; {@code courseId} null means all of them. */
    @Query(value = DocumentSummary.SELECT + "WHERE (:courseId IS NULL OR c.id = :courseId)",
            countQuery = "SELECT COUNT(d) FROM Document d WHERE (:courseId IS NULL OR d.course.id = :courseId)")
    Page<DocumentSummary> findAllSummaries(@Param("courseId") Long courseId, Pageable pageable);

    /**
     * Documents of the allowed courses (AccessControlService.Scope); TEACHER_ONLY ones, and ones the AI is still
     * classifying (PENDING/PROCESSING), only with {@code includeTeacherOnly}. {@code courseId} null means all allowed
     * courses.
     */
    @Query(value = DocumentSummary.SELECT + """
            WHERE c.id IN :courseIds
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:includeTeacherOnly = TRUE
                   OR (d.visibility <> com.university.docassistant.entity.DocumentVisibility.TEACHER_ONLY
                       AND d.aiStatus NOT IN (com.university.docassistant.entity.AiStatus.PENDING,
                                              com.university.docassistant.entity.AiStatus.PROCESSING)))
            """,
            countQuery = """
            SELECT COUNT(d) FROM Document d
            WHERE d.course.id IN :courseIds
              AND (:courseId IS NULL OR d.course.id = :courseId)
              AND (:includeTeacherOnly = TRUE
                   OR (d.visibility <> com.university.docassistant.entity.DocumentVisibility.TEACHER_ONLY
                       AND d.aiStatus NOT IN (com.university.docassistant.entity.AiStatus.PENDING,
                                              com.university.docassistant.entity.AiStatus.PROCESSING)))
            """)
    Page<DocumentSummary> findSummariesInCourses(@Param("courseIds") Collection<Long> courseIds,
                                                 @Param("includeTeacherOnly") boolean includeTeacherOnly,
                                                 @Param("courseId") Long courseId,
                                                 Pageable pageable);

    @Query(DocumentSummary.SELECT + "WHERE d.id IN :ids")
    List<DocumentSummary> findSummariesByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Ids of documents whose masked text (US-09: text_tsv is built from masked_text) matches {@code query} (web-search syntax: words are ANDed,
     * "quoted phrase", OR, -excluded), best match first. {@code all} (ADMIN) lifts the course filter, which is
     * the same as in {@link #findSummariesInCourses}. Uses the GIN index from DocumentSearchSchema.
     * The page must be unsorted: the order is fixed by relevance.
     */
    @Query(value = """
            SELECT d.id FROM documents d
            WHERE d.text_tsv @@ websearch_to_tsquery('russian', :query)
              AND (:all
                   OR (d.course_id IN (:courseIds)
                       AND (:includeTeacherOnly
                            OR (d.visibility <> 'TEACHER_ONLY'
                                AND d.ai_status NOT IN ('PENDING', 'PROCESSING')))))
            ORDER BY ts_rank(d.text_tsv, websearch_to_tsquery('russian', :query)) DESC, d.uploaded_at DESC, d.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM documents d
            WHERE d.text_tsv @@ websearch_to_tsquery('russian', :query)
              AND (:all
                   OR (d.course_id IN (:courseIds)
                       AND (:includeTeacherOnly
                            OR (d.visibility <> 'TEACHER_ONLY'
                                AND d.ai_status NOT IN ('PENDING', 'PROCESSING')))))
            """,
            nativeQuery = true)
    Page<Long> searchVisibleIds(@Param("query") String query,
                                @Param("all") boolean all,
                                @Param("courseIds") Collection<Long> courseIds,
                                @Param("includeTeacherOnly") boolean includeTeacherOnly,
                                Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "course"})
    Optional<Document> findWithOwnerById(Long id);

    /** What OCR needs: the stored file and the course teacher's e-mail (kept readable by the masking). */
    record OcrSource(Long id, String storedFileName, String originalFileName, String contentType, String teacherEmail) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$OcrSource(
                d.id, d.storedFileName, d.originalFileName, d.contentType, t.email)
            FROM Document d LEFT JOIN d.course c LEFT JOIN c.teacher t WHERE d.id = :id
            """)
    Optional<OcrSource> findOcrSource(@Param("id") Long id);

    /** Moves ocr_status only from {@code from}: 1 when this caller got the document, 0 when not in that state. */
    @Modifying
    @Query("UPDATE Document d SET d.ocrStatus = :to WHERE d.id = :id AND d.ocrStatus = :from")
    int changeOcrStatus(@Param("id") Long id, @Param("from") OcrStatus from, @Param("to") OcrStatus to);

    /** Stores OCR-recognized text exactly like uploaded text (original, masked copy, snippet, counts) and marks it. */
    @Modifying
    @Query("""
            UPDATE Document d SET d.extractedText = :text, d.textTruncated = :truncated, d.maskedText = :maskedText,
                d.textSnippet = :snippet, d.piiCount = :piiCount, d.piiTypes = :piiTypes, d.piiScan = :fingerprint,
                d.textFromOcr = true, d.ocrStatus = com.university.docassistant.entity.OcrStatus.DONE
            WHERE d.id = :id
            """)
    int updateRecognizedText(@Param("id") Long id, @Param("text") String text, @Param("truncated") boolean truncated,
                             @Param("maskedText") String maskedText, @Param("snippet") String snippet,
                             @Param("piiCount") int piiCount, @Param("piiTypes") String piiTypes,
                             @Param("fingerprint") String fingerprint);

    /** A document whose text must be masked (again), with its course teacher's e-mail (kept readable). */
    record PrivacySource(Long id, String extractedText, String teacherEmail, String piiScan) {
    }

    /**
     * Next documents with text whose masked copy is missing or was made with other settings than {@code fingerprint},
     * in id order after {@code afterId}: DocumentPrivacyBackfill walks them in batches.
     */
    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$PrivacySource(
                d.id, d.extractedText, t.email, d.piiScan)
            FROM Document d LEFT JOIN d.course c LEFT JOIN c.teacher t
            WHERE d.id > :afterId AND d.extractedText IS NOT NULL
              AND (d.piiScan IS NULL OR d.piiScan <> :fingerprint)
            ORDER BY d.id
            """)
    List<PrivacySource> findPrivacyBatch(@Param("fingerprint") String fingerprint, @Param("afterId") Long afterId,
                                         Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Document d SET d.maskedText = :maskedText, d.textSnippet = :snippet, d.piiCount = :piiCount,
                d.piiTypes = :piiTypes, d.piiScan = :fingerprint
            WHERE d.id = :id
            """)
    int updatePrivacy(@Param("id") Long id, @Param("maskedText") String maskedText, @Param("snippet") String snippet,
                      @Param("piiCount") int piiCount, @Param("piiTypes") String piiTypes,
                      @Param("fingerprint") String fingerprint);

    /**
     * What AI analysis needs from a document, without loading the entity and its associations. {@code maskedText} is
     * the copy without personal data (US-09): the original never goes to the AI.
     */
    record AnalysisSource(Long id, DocumentCategory category, String maskedText, Long ownerId, Long courseId) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$AnalysisSource(
                d.id, d.category, d.maskedText, d.owner.id, c.id)
            FROM Document d LEFT JOIN d.course c WHERE d.id = :id
            """)
    Optional<AnalysisSource> findAnalysisSource(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE Document d SET d.aiSummary = :summary, d.aiTopics = :topics, d.aiAnalyzedAt = :analyzedAt
            WHERE d.id = :id
            """)
    int updateAnalysis(@Param("id") Long id,
                       @Param("summary") String summary,
                       @Param("topics") String topics,
                       @Param("analyzedAt") LocalDateTime analyzedAt);

    /** What a status poll needs from a document, with what deciding access to it needs. */
    record StatusRow(Long id, Long courseId, DocumentVisibility visibility, AiStatus aiStatus,
                     DocumentCategory category, Double confidence, OcrStatus ocrStatus, boolean textFromOcr) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$StatusRow(
                d.id, d.course.id, d.visibility, d.aiStatus, d.category, dc.confidenceScore, d.ocrStatus, d.textFromOcr)
            FROM Document d LEFT JOIN DocumentClassification dc ON dc.document = d
            WHERE d.id IN :ids
            """)
    List<StatusRow> findStatusRows(@Param("ids") Collection<Long> ids);

    /**
     * What background classification needs from a document, without loading the entity and its associations.
     * {@code maskedText} is the copy without personal data (US-09).
     */
    record ClassificationSource(Long id, String originalFileName, String maskedText, String ownerEmail) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$ClassificationSource(
                d.id, d.originalFileName, d.maskedText, o.email)
            FROM Document d JOIN d.owner o WHERE d.id = :id
            """)
    Optional<ClassificationSource> findClassificationSource(@Param("id") Long id);

    /** Moves a document from {@code from} to {@code to}; 0 when its status was something else (or it is gone). */
    @Modifying
    @Query("UPDATE Document d SET d.aiStatus = :to WHERE d.id = :id AND d.aiStatus = :from")
    int changeAiStatus(@Param("id") Long id, @Param("from") AiStatus from, @Param("to") AiStatus to);

    @Modifying
    @Query("UPDATE Document d SET d.aiStatus = :status WHERE d.id = :id")
    int setAiStatus(@Param("id") Long id, @Param("status") AiStatus status);

    @Query("SELECT d.aiStatus FROM Document d WHERE d.id = :id")
    Optional<AiStatus> findAiStatusById(@Param("id") Long id);

    /** The classification outcome in one statement; 0 when the document is gone. */
    @Modifying
    @Query("""
            UPDATE Document d SET d.category = :category, d.type = :type, d.aiStatus = :status
            WHERE d.id = :id
            """)
    int updateClassification(@Param("id") Long id,
                             @Param("category") DocumentCategory category,
                             @Param("type") DocumentType type,
                             @Param("status") AiStatus status);

    /** Rule A for documents the AI found to be EXAM_ANSWERS; 0 when it already was TEACHER_ONLY. */
    @Modifying
    @Query("""
            UPDATE Document d SET d.visibility = com.university.docassistant.entity.DocumentVisibility.TEACHER_ONLY
            WHERE d.id = :id AND d.visibility <> com.university.docassistant.entity.DocumentVisibility.TEACHER_ONLY
            """)
    int forceTeacherOnly(@Param("id") Long id);

    /** Row lock that serializes concurrent analyses of one document, so their task sets never mix. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d.id FROM Document d WHERE d.id = :id")
    Optional<Long> lockById(@Param("id") Long id);

    @Query("SELECT d.storedFileName FROM Document d WHERE d.owner.id = :ownerId")
    List<String> findStoredFileNamesByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT d.id FROM Document d WHERE d.owner.id = :ownerId")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);

    @Modifying
    @Query("DELETE FROM Document d WHERE d.owner.id = :ownerId")
    int deleteByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d")
    long sumFileSize();
}
