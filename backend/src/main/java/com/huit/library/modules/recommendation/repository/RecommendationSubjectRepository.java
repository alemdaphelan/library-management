package com.huit.library.modules.recommendation.repository;

import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface RecommendationSubjectRepository extends JpaRepository<RecommendationSubjectEntity, Long> {
    Optional<RecommendationSubjectEntity> findByCodeAndActiveTrue(String code);

    List<RecommendationSubjectEntity> findByActiveTrueOrderByNameAsc();

    List<RecommendationSubjectEntity> findBySubjectIdInAndActiveTrue(Collection<Long> subjectIds);
}
