package com.huit.library.modules.reporting.repository;

import com.huit.library.modules.reporting.entity.AcademicMajorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AcademicMajorRepository extends JpaRepository<AcademicMajorEntity, String> {
    List<AcademicMajorEntity> findAllByOrderByMajorNameAscMajorIdAsc();
}
