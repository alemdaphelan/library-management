package com.huit.library.modules.catalog.entity;

import com.huit.library.core.entity.BaseEntity;
import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "shelf_subject_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shelf_subject_rule",
                columnNames = {"shelf_id", "subject_id"}
        )
)
public class ShelfSubjectRuleEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ruleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shelf_id", nullable = false)
    private ShelfEntity shelf;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private RecommendationSubjectEntity subject;

    @Column(nullable = false)
    private Boolean active = true;
}
