package com.huit.library.modules.penalty.repository;

import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PenaltyCaseRepository extends JpaRepository<PenaltyCaseEntity, Long> {
    List<PenaltyCaseEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<PenaltyCaseEntity> findAllByOrderByCreatedAtDesc();
    List<PenaltyCaseEntity> findByStatusIn(List<String> statuses);
    boolean existsByLoanDetailIdAndStatusNotIn(Long loanDetailId, List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select penaltyCase from PenaltyCaseEntity penaltyCase where penaltyCase.caseId = :caseId")
    Optional<PenaltyCaseEntity> findByIdForUpdate(@Param("caseId") Long caseId);
}
