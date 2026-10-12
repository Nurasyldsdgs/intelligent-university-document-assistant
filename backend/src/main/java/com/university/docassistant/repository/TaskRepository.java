package com.university.docassistant.repository;

import com.university.docassistant.dto.TaskResponse;
import com.university.docassistant.entity.Task;
import com.university.docassistant.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * List methods return {@link TaskResponse} rows built in the query; see {@link TaskResponse#SELECT}.
 * A student's tasks are copies of the ACTIVE assignments of the courses they are enrolled in: {@link #fanOut} creates
 * the missing ones and {@link #syncWithAssignments} carries over edited titles and deadlines; statuses are kept.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query(value = TaskResponse.SELECT + "WHERE t.user.id = :userId",
            countQuery = "SELECT COUNT(t) FROM Task t WHERE t.user.id = :userId")
    Page<TaskResponse> findViewsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = TaskResponse.SELECT + "WHERE t.user.id = :userId AND t.status = :status",
            countQuery = "SELECT COUNT(t) FROM Task t WHERE t.user.id = :userId AND t.status = :status")
    Page<TaskResponse> findViewsByUserIdAndStatus(@Param("userId") Long userId,
                                                  @Param("status") TaskStatus status,
                                                  Pageable pageable);

    /** Not completed, deadline from {@code from} on, nearest first: the "Upcoming deadlines" widget. */
    @Query(value = TaskResponse.SELECT + """
            WHERE t.user.id = :userId AND t.status <> com.university.docassistant.entity.TaskStatus.COMPLETED
              AND t.deadline >= :from
            ORDER BY t.deadline
            """,
            countQuery = """
            SELECT COUNT(t) FROM Task t
            WHERE t.user.id = :userId AND t.status <> com.university.docassistant.entity.TaskStatus.COMPLETED
              AND t.deadline >= :from
            """)
    Page<TaskResponse> findUpcomingViews(@Param("userId") Long userId,
                                         @Param("from") LocalDateTime from,
                                         Pageable pageable);

    @Query(TaskResponse.SELECT + "WHERE t.id = :id")
    Optional<TaskResponse> findViewById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Task t SET t.status = :status WHERE t.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") TaskStatus status);

    @Query("SELECT t.user.id FROM Task t WHERE t.id = :id")
    Optional<Long> findUserIdById(@Param("id") Long id);

    /** Owner, course and assignment of a task: what deciding who may submit to or read it needs. */
    record TaskAccess(Long id, Long userId, Long courseId, Long assignmentId) {
    }

    @Query("""
            SELECT new com.university.docassistant.repository.TaskRepository$TaskAccess(t.id, t.user.id, c.id, a.id)
            FROM Task t LEFT JOIN t.course c LEFT JOIN t.assignment a WHERE t.id = :id
            """)
    Optional<TaskAccess> findAccessById(@Param("id") Long id);

    /**
     * Gives every student enrolled in the course a PENDING task for each ACTIVE assignment they do not have yet.
     * Idempotent; returns the number of tasks created.
     */
    @Modifying
    @Query(value = """
            INSERT INTO tasks (title, description, deadline, status, document_id, course_id, user_id, assignment_id)
            SELECT a.title, a.description, a.deadline, 'PENDING', a.source_document_id, a.course_id, e.student_id, a.id
            FROM assignments a JOIN course_students e ON e.course_id = a.course_id
            WHERE a.course_id = :courseId AND a.status = 'ACTIVE'
              AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.assignment_id = a.id AND t.user_id = e.student_id)
            """, nativeQuery = true)
    int fanOut(@Param("courseId") Long courseId);

    /** Copies title, description and deadline of the course's assignments into their tasks. */
    @Modifying
    @Query(value = """
            UPDATE tasks t SET title = a.title, description = a.description, deadline = a.deadline
            FROM assignments a
            WHERE t.assignment_id = a.id AND a.course_id = :courseId
              AND (t.title <> a.title OR t.description IS DISTINCT FROM a.description
                   OR t.deadline IS DISTINCT FROM a.deadline)
            """, nativeQuery = true)
    int syncWithAssignments(@Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.assignment.id IN :assignmentIds")
    int deleteByAssignmentIdIn(@Param("assignmentIds") Collection<Long> assignmentIds);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.assignment.id IN (SELECT a.id FROM Assignment a WHERE a.sourceDocument.id = :documentId)")
    int deleteByAssignmentSourceDocumentId(@Param("documentId") Long documentId);

    @Modifying
    @Query("""
            DELETE FROM Task t WHERE t.assignment.id IN (
                SELECT a.id FROM Assignment a WHERE a.sourceDocument.owner.id = :ownerId)
            """)
    int deleteByAssignmentSourceDocumentOwnerId(@Param("ownerId") Long ownerId);

    /** A student leaving a course loses that course's tasks. */
    @Modifying
    @Query("DELETE FROM Task t WHERE t.course.id = :courseId AND t.user.id = :userId")
    int deleteByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.document.id = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.document.id IN (SELECT d.id FROM Document d WHERE d.owner.id = :ownerId)")
    int deleteByDocumentOwnerId(@Param("ownerId") Long ownerId);
}
