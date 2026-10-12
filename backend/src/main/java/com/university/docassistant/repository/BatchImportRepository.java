package com.university.docassistant.repository;

import com.university.docassistant.entity.BatchImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface BatchImportRepository extends JpaRepository<BatchImport, Long> {

    /** A row of the course's import history; facultyName is null once the uploader's account is deleted. */
    record BatchImportRow(Long id, String archiveName, int totalFiles, int processedFiles, String facultyName,
                          LocalDateTime createdAt) {
    }

    /** Newest first; the order is fixed here, so the page must be unsorted. */
    @Query(value = """
            SELECT new com.university.docassistant.repository.BatchImportRepository$BatchImportRow(
                b.id, b.archiveName, b.totalFiles, b.processedFiles, COALESCE(f.fullName, f.email), b.createdAt)
            FROM BatchImport b LEFT JOIN b.faculty f
            WHERE b.course.id = :courseId
            ORDER BY b.createdAt DESC, b.id DESC
            """,
            countQuery = "SELECT COUNT(b) FROM BatchImport b WHERE b.course.id = :courseId")
    Page<BatchImportRow> findRowsByCourseId(@Param("courseId") Long courseId, Pageable pageable);

    /** [batchImportId, count] of documents the AI is still classifying, for one page of imports in one query. */
    @Query("""
            SELECT d.batchImport.id, COUNT(d) FROM Document d
            WHERE d.batchImport.id IN :ids
              AND d.aiStatus IN (com.university.docassistant.entity.AiStatus.PENDING,
                                 com.university.docassistant.entity.AiStatus.PROCESSING)
            GROUP BY d.batchImport.id
            """)
    List<Object[]> countPendingByBatchImportIds(@Param("ids") Collection<Long> ids);
}
