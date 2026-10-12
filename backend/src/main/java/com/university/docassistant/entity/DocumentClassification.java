package com.university.docassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
 * Result of the AI type classification of one document (US-05): the category the model chose and how sure it was.
 * One row per document; it goes away with the document (ON DELETE CASCADE).
 */
@Entity
@Table(name = "document_classifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private Document document;

    /** Name of the chosen {@link DocumentCategory}, kept as text so retired categories stay readable. */
    @Column(name = "category_name", nullable = false, length = 30)
    private String categoryName;

    /** 0.0-1.0; null when the model gave no confidence. */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "classified_at", nullable = false)
    private LocalDateTime classifiedAt;

    @PrePersist
    protected void onCreate() {
        if (classifiedAt == null) {
            classifiedAt = LocalDateTime.now();
        }
    }
}
