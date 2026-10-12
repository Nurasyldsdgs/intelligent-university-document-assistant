package com.university.docassistant.service;

import com.university.docassistant.dto.AssignmentDtos.AssignmentResponse;
import com.university.docassistant.dto.AssignmentDtos.CreateAssignmentRequest;
import com.university.docassistant.dto.PageResponse;
import com.university.docassistant.dto.TaskExtractDto;
import com.university.docassistant.entity.Assignment;
import com.university.docassistant.entity.AssignmentStatus;
import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.repository.AssignmentRepository;
import com.university.docassistant.repository.CourseRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.security.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Document -> Assignment -> student Task. Assignments belong to a course: the AI finds them in its documents, its
 * teacher can add more. Every student enrolled in the course gets a task per ACTIVE assignment, also when enrolling
 * later (CourseService calls {@link #handOut}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final DocumentRepository documentRepository;
    private final AccessControlService access;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> list(Long courseId, String email, Pageable pageable) {
        access.authorizeCourse(access.currentUser(email), courseId, Permission.VIEW_COURSE);
        return PageResponse.of(assignmentRepository.findViewsByCourseId(courseId, pageable));
    }

    @Transactional
    public AssignmentResponse create(Long courseId, CreateAssignmentRequest request, String email) {
        User user = access.currentUser(email);
        Course course = access.authorizeCourse(user, courseId, Permission.MANAGE_ASSIGNMENTS);
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title(request.title().strip())
                .requirements(request.description() == null || request.description().isBlank()
                        ? null : request.description().strip())
                .deadline(request.deadline())
                .course(course)
                .build());
        int handedOut = handOut(courseId);
        auditService.record(user.getEmail(), AuditService.ASSIGNMENT_CREATED,
                "Assignment " + assignment.getId() + " \"" + assignment.getTitle() + "\" in course " + courseId);
        log.info("User {} created assignment {} in course {} ({} student task(s))",
                user.getEmail(), assignment.getId(), courseId, handedOut);
        return assignmentRepository.findViewById(assignment.getId())
                .orElseThrow(() -> ApiException.notFound("Assignment not found"));
    }

    /** Removes the assignment and its students' tasks; for the course teacher or ADMIN. */
    @Transactional
    public void delete(Long id, String email) {
        User user = access.currentUser(email);
        Long courseId = assignmentRepository.findCourseIdById(id)
                .orElseThrow(() -> ApiException.notFound("Assignment not found"));
        access.authorizeCourse(user, courseId, Permission.MANAGE_ASSIGNMENTS);
        taskRepository.deleteByAssignmentIdIn(List.of(id));
        assignmentRepository.deleteById(id);
        auditService.record(user.getEmail(), AuditService.ASSIGNMENT_DELETED,
                "Assignment " + id + " of course " + courseId);
        log.info("User {} deleted assignment {} of course {}", user.getEmail(), id, courseId);
    }

    /**
     * Makes the document's assignments match what the AI found: items already there (same title and deadline) are
     * kept with their students' progress, new ones are created, vanished ones are removed. Then every enrolled student
     * gets their tasks. Runs in the caller's transaction; returns the number of assignments.
     */
    @Transactional
    public int replaceFromDocument(Long documentId, Long courseId, List<TaskExtractDto> found) {
        Map<String, Assignment> existing = new HashMap<>();
        for (Assignment assignment : assignmentRepository.findBySourceDocumentId(documentId)) {
            existing.putIfAbsent(key(assignment.getTitle(), assignment), assignment);
        }
        List<Assignment> kept = new ArrayList<>();
        for (TaskExtractDto item : found) {
            Assignment assignment = existing.remove(key(item.title(), item));
            if (assignment == null) {
                assignment = Assignment.builder()
                        .title(item.title())
                        .deadline(item.deadline())
                        .status(AssignmentStatus.ACTIVE)
                        .course(courseRepository.getReferenceById(courseId))
                        .sourceDocument(documentRepository.getReferenceById(documentId))
                        .build();
            }
            assignment.setRequirements(item.description());
            kept.add(assignment);
        }
        if (!existing.isEmpty()) {
            List<Long> removed = existing.values().stream().map(Assignment::getId).toList();
            taskRepository.deleteByAssignmentIdIn(removed);
            assignmentRepository.deleteAllByIdInBatch(removed);
        }
        assignmentRepository.saveAll(kept);
        assignmentRepository.flush();
        handOut(courseId);
        return kept.size();
    }

    /** Creates the missing student tasks of the course and refreshes the others; returns how many were created. */
    @Transactional
    public int handOut(Long courseId) {
        taskRepository.syncWithAssignments(courseId);
        return taskRepository.fanOut(courseId);
    }

    private static String key(String title, Assignment assignment) {
        return title.strip().toLowerCase(Locale.ROOT) + "|" + Objects.toString(assignment.getDeadline(), "");
    }

    private static String key(String title, TaskExtractDto item) {
        return title.strip().toLowerCase(Locale.ROOT) + "|" + Objects.toString(item.deadline(), "");
    }
}
