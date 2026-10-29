package com.huit.library.modules.recommendation.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "department_subject_affinities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_department_subject",
                columnNames = {"department_key", "subject_id"}
        )
)
public class DepartmentSubjectAffinityEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long affinityId;

    @Column(nullable = false, length = 255)
    private String departmentKey;

    @Column(nullable = false, length = 255)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private RecommendationSubjectEntity subject;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal weight;

    @Column(nullable = false, length = 64)
    private String source = "HUIT_2026";

    @Column(nullable = false)
    private Boolean active = true;
}
