package com.university.docassistant.repository;

import com.university.docassistant.entity.AiStatus;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentType;
import com.university.docassistant.entity.DocumentVisibility;
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
     * Ids of documents whose extracted text matches {@code query} (web-search syntax: words are ANDed,
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

    /** What AI analysis needs from a document, without loading the entity and its associations. */
    record AnalysisSource(Long id, DocumentCategory category, String extractedText, Long ownerId, Long courseId) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$AnalysisSource(
                d.id, d.category, d.extractedText, d.owner.id, c.id)
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
                     DocumentCategory category, Double confidence) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$StatusRow(
                d.id, d.course.id, d.visibility, d.aiStatus, d.category, dc.confidenceScore)
            FROM Document d LEFT JOIN DocumentClassification dc ON dc.document = d
            WHERE d.id IN :ids
            """)
    List<StatusRow> findStatusRows(@Param("ids") Collection<Long> ids);

    /** What background classification needs from a document, without loading the entity and its associations. */
    record ClassificationSource(Long id, String originalFileName, String extractedText, String ownerEmail) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.DocumentRepository$ClassificationSource(
                d.id, d.originalFileName, d.extractedText, o.email)
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

    @Modifying
    @Query("DELETE FROM Document d WHERE d.owner.id = :ownerId")
    int deleteByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d")
    long sumFileSize();
}
