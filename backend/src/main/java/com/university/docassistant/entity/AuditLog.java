package com.university.docassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One security-relevant event for the admin's system log; written by AuditService, never changed. */
@Entity
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_log_created_at", columnList = "created_at"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Null for events without a signed-in user. */
    @Column(name = "actor_email")
    private String actorEmail;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 1_000)
    private String details;
}
