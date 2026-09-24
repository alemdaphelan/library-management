package com.huit.library.modules.digital.repository;

import com.huit.library.modules.digital.entity.DigitalUsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DigitalUsageLogRepository extends JpaRepository<DigitalUsageLogEntity, Long> {
}
