package com.university.docassistant.repository;

import com.university.docassistant.dto.SubmissionDtos.SubmissionResponse;
import com.university.docassistant.entity.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * List methods return {@link SubmissionResponse} rows built in the query; see {@link SubmissionResponse#SELECT}.
 */
@Repository
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    /** What deciding access to one submission and serving its file needs, without loading the entities. */
    record SubmissionAccess(Long id, Long studentId, Long courseId, String originalFileName, String contentType,
                            String storedFileName) {
    }

    @Query(SubmissionResponse.SELECT + "WHERE t.id = :taskId ORDER BY s.submittedAt DESC, s.id DESC")
    List<SubmissionResponse> findViewsByTaskId(@Param("taskId") Long taskId);

    @Query(SubmissionResponse.SELECT + "WHERE a.id = :assignmentId ORDER BY u.fullName, s.submittedAt DESC, s.id DESC")
    List<SubmissionResponse> findViewsByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Query(SubmissionResponse.SELECT + "WHERE s.id IN :ids ORDER BY s.submittedAt DESC, s.id DESC")
    List<SubmissionResponse> findViewsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT new com.university.docassistant.repository.TaskSubmissionRepository$SubmissionAccess(
                s.id, s.student.id, t.course.id, s.originalFileName, s.contentType, s.storedFileName)
            FROM TaskSubmission s JOIN s.task t WHERE s.id = :id
            """)
    Optional<SubmissionAccess> findAccessById(@Param("id") Long id);

    /** Files of the user's own submissions; erased from disk when the user is deleted. */
    @Query("SELECT s.storedFileName FROM TaskSubmission s WHERE s.student.id = :studentId")
    List<String> findStoredFileNamesByStudentId(@Param("studentId") Long studentId);
}
