package com.university.docassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * One ZIP archive imported into a course (US-06): how many files it held and how many became documents.
 * {@code faculty} is the teacher or ADMIN who uploaded it; null once that account is deleted.
 */
@Entity
@Table(name = "batch_imports", indexes = {
        @Index(name = "idx_batch_imports_course_id", columnList = "course_id"),
        @Index(name = "idx_batch_imports_faculty_id", columnList = "faculty_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private Course course;

    @Column(name = "archive_name", nullable = false)
    private String archiveName;

    /** Files in the archive, supported or not. */
    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    /** Files that became documents of the course. */
    @Column(name = "processed_files", nullable = false)
    private int processedFiles;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @ToString.Exclude
    private User faculty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
