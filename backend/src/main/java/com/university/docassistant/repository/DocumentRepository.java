package com.university.docassistant.repository;

import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
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

    /** For ADMIN: every document, those without a course included; {@code courseId} null means all of them. */
    @Query(value = DocumentSummary.SELECT + "WHERE (:courseId IS NULL OR c.id = :courseId)",
            countQuery = "SELECT COUNT(d) FROM Document d WHERE (:courseId IS NULL OR d.course.id = :courseId)")
    Page<DocumentSummary> findAllSummaries(@Param("courseId") Long courseId, Pageable pageable);

    /**
     * Documents of the allowed courses (AccessControlService.Scope); TEACHER_ONLY ones only with
     * {@code includeTeacherOnly}. {@code courseId} null means all allowed courses.
     */
    @Query(value = DocumentSummary.SELECT + """
            WHERE c.id IN :courseIds
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:includeTeacherOnly = TRUE
                   OR d.visibility <> com.university.docassistant.entity.DocumentVisibility.TEACHER_ONLY)
            """,
            countQuery = """
            SELECT COUNT(d) FROM Document d
            WHERE d.course.id IN :courseIds
              AND (:courseId IS NULL OR d.course.id = :courseId)
              AND (:includeTeacherOnly = TRUE
                   OR d.visibility <> com.university.docassistant.entity.DocumentVisibility.TEACHER_ONLY)
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
                       AND (:includeTeacherOnly OR d.visibility <> 'TEACHER_ONLY')))
            ORDER BY ts_rank(d.text_tsv, websearch_to_tsquery('russian', :query)) DESC, d.uploaded_at DESC, d.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM documents d
            WHERE d.text_tsv @@ websearch_to_tsquery('russian', :query)
              AND (:all
                   OR (d.course_id IN (:courseIds)
                       AND (:includeTeacherOnly OR d.visibility <> 'TEACHER_ONLY')))
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
