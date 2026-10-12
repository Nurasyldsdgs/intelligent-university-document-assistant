package com.university.docassistant.service;

import com.university.docassistant.entity.AuditLog;
import com.university.docassistant.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The admin's system log. Each event is written in its own transaction, so it is kept even when the caller's
 * transaction is read-only or rolls back (a denied request), and a failed write never fails the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    public static final String DOCUMENT_DELETED = "DOCUMENT_DELETED";
    public static final String COURSE_CREATED = "COURSE_CREATED";
    public static final String COURSE_TEACHER_CHANGED = "COURSE_TEACHER_CHANGED";
    public static final String STUDENT_ENROLLED = "STUDENT_ENROLLED";
    public static final String STUDENT_UNENROLLED = "STUDENT_UNENROLLED";
    public static final String ASSIGNMENT_CREATED = "ASSIGNMENT_CREATED";
    public static final String ASSIGNMENT_DELETED = "ASSIGNMENT_DELETED";
    public static final String TASK_SUBMITTED = "TASK_SUBMITTED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String USER_DELETED = "USER_DELETED";

    private static final int MAX_DETAILS_LENGTH = 1_000;

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorEmail, String action, String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .createdAt(LocalDateTime.now())
                    .actorEmail(actorEmail)
                    .action(action)
                    .details(details == null || details.length() <= MAX_DETAILS_LENGTH
                            ? details : details.substring(0, MAX_DETAILS_LENGTH))
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Could not write audit event {} of {}: {}", action, actorEmail, ex.toString());
        }
    }
}
