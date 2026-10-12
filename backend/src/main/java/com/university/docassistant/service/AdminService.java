package com.university.docassistant.service;

import com.university.docassistant.dto.AdminDtos.AdminStatsResponse;
import com.university.docassistant.dto.AdminDtos.AuditLogResponse;
import com.university.docassistant.dto.AdminDtos.AdminUserResponse;
import com.university.docassistant.dto.PageResponse;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.repository.AssignmentRepository;
import com.university.docassistant.repository.AuditLogRepository;
import com.university.docassistant.repository.CourseRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.EnrollmentRepository;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.repository.TaskSubmissionRepository;
import com.university.docassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final CourseRepository courseRepository;
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll(Sort.by("id")).stream().map(AdminService::toResponse).toList();
    }

    /**
     * The changed user's existing JWT carries the old role, so JwtAuthenticationFilter rejects it
     * and they have to log in again - the new role takes effect immediately. Course relations of the old role are
     * dropped so they cannot grant access under the new one: a former STUDENT leaves their courses (and their tasks),
     * a former TEACHER's courses are left without a teacher for ADMIN to reassign.
     */
    @Transactional
    public AdminUserResponse updateRole(Long id, Role role, String adminEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        // An admin demoting themselves could leave the system without any admin.
        if (user.getEmail().equals(adminEmail) && role != Role.ROLE_ADMIN) {
            throw ApiException.badRequest("You cannot change your own role");
        }

        if (user.getRole() != role) {
            log.info("Admin {} changed role of {} from {} to {}", adminEmail, user.getEmail(), user.getRole(), role);
            if (user.getRole() == Role.ROLE_STUDENT) {
                taskRepository.deleteByUserId(user.getId());
                enrollmentRepository.deleteByStudentId(user.getId());
            } else if (user.getRole() == Role.ROLE_TEACHER) {
                courseRepository.detachTeacher(user.getId());
            }
            auditService.record(adminEmail, AuditService.ROLE_CHANGED,
                    user.getEmail() + ": " + user.getRole() + " -> " + role);
            user.setRole(role);
            userRepository.save(user);
        }
        return toResponse(user);
    }

    /**
     * Deletes the user together with their documents (and the assignments found in them), enrollments and tasks:
     * DB rows first, then the files on disk.
     * Courses they taught are kept without a teacher.
     * Files are erased only after the transaction commits, so a rolled-back delete never loses content.
     */
    @Transactional
    public void deleteUser(Long userId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (user.getEmail().equals(adminEmail)) {
            throw ApiException.badRequest("You cannot delete your own account");
        }

        List<String> storedFileNames = new ArrayList<>(documentRepository.findStoredFileNamesByOwnerId(userId));
        // Their submission rows go with their tasks (ON DELETE CASCADE); the files are erased below.
        storedFileNames.addAll(submissionRepository.findStoredFileNamesByStudentId(userId));

        taskRepository.deleteByUserId(userId);
        taskRepository.deleteByAssignmentSourceDocumentOwnerId(userId);
        assignmentRepository.deleteBySourceDocumentOwnerId(userId);
        taskRepository.deleteByDocumentOwnerId(userId);
        documentRepository.deleteByOwnerId(userId);
        enrollmentRepository.deleteByStudentId(userId);
        courseRepository.detachTeacher(userId);
        userRepository.delete(user);
        auditService.record(adminEmail, AuditService.USER_DELETED,
                user.getEmail() + " with " + storedFileNames.size() + " document(s)");

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storedFileNames.forEach(fileStorageService::delete);
            }
        });
        log.info("Admin {} deleted user {} with {} document(s)", adminEmail, user.getEmail(), storedFileNames.size());
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        return new AdminStatsResponse(
                userRepository.count(),
                documentRepository.count(),
                documentRepository.sumFileSize(),
                courseRepository.count(),
                enrollmentRepository.count(),
                assignmentRepository.count());
    }

    /** The system log, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> logs(Pageable pageable) {
        return PageResponse.of(auditLogRepository.findAll(pageable).map(entry -> new AuditLogResponse(
                entry.getId(), entry.getCreatedAt(), entry.getActorEmail(), entry.getAction(), entry.getDetails())));
    }

    private static AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
