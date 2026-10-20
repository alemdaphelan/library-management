package com.huit.library.modules.recommendation.entity;

import com.huit.library.core.entity.BaseEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "book_subject_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_book_recommendation_subject",
                columnNames = {"book_id", "subject_id"}
        )
)
public class BookSubjectAssignmentEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private RecommendationSubjectEntity subject;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(nullable = false, length = 64)
    private String classificationSource;

    @Column(length = 500)
    private String evidence;

    @Column(nullable = false)
    private Boolean primarySubject = false;
}
