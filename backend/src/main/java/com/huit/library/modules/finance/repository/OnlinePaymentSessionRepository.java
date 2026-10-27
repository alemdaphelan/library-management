package com.huit.library.modules.finance.repository;

import com.huit.library.modules.finance.entity.OnlinePaymentSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OnlinePaymentSessionRepository extends JpaRepository<OnlinePaymentSessionEntity, Long> {
    Optional<OnlinePaymentSessionEntity> findByReferenceAndUserId(String reference, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from OnlinePaymentSessionEntity payment where payment.reference = :reference")
    Optional<OnlinePaymentSessionEntity> findByReferenceForUpdate(@Param("reference") String reference);
}
