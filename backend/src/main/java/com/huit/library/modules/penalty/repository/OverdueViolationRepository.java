package com.huit.library.modules.penalty.repository;

import com.huit.library.modules.penalty.entity.OverdueViolationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverdueViolationRepository extends JpaRepository<OverdueViolationEntity, Long> {
    boolean existsByTicketIdAndAcademicYear(Long ticketId, String academicYear);
    long countByUserIdAndAcademicYear(java.util.UUID userId, String academicYear);
}
