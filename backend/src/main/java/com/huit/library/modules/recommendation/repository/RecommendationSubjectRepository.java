package com.huit.library.modules.recommendation.repository;

import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationSubjectRepository extends JpaRepository<RecommendationSubjectEntity, Long> {
    Optional<RecommendationSubjectEntity> findByCodeAndActiveTrue(String code);
}
