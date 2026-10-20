package com.huit.library.modules.recommendation.repository;

import com.huit.library.modules.recommendation.entity.DepartmentSubjectAffinityEntity;
import com.huit.library.modules.catalog.dto.DepartmentOptionDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepartmentSubjectAffinityRepository
        extends JpaRepository<DepartmentSubjectAffinityEntity, Long> {
    List<DepartmentSubjectAffinityEntity> findByDepartmentKeyAndActiveTrueOrderByWeightDesc(
            String departmentKey);

    @Query("""
            select affinity
            from DepartmentSubjectAffinityEntity affinity
            join fetch affinity.subject
            where affinity.active = true
            order by affinity.departmentKey, affinity.weight desc, affinity.subject.subjectId
            """)
    List<DepartmentSubjectAffinityEntity> findAllActiveForEvaluation();

    @Query("""
            select distinct new com.huit.library.modules.catalog.dto.DepartmentOptionDTO(
                affinity.departmentKey, affinity.displayName)
            from DepartmentSubjectAffinityEntity affinity
            where affinity.active = true
            order by affinity.displayName
            """)
    List<DepartmentOptionDTO> findDistinctActiveDepartments();
}
