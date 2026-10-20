package com.huit.library.modules.finance.repository;

import com.huit.library.modules.finance.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    @Query("select sum(entry.amount) from TransactionEntity entry where upper(entry.type) = upper(:type)")
    BigDecimal sumAmountByType(@Param("type") String type);

    boolean existsByDedupKey(String dedupKey);
    Optional<TransactionEntity> findByDedupKey(String dedupKey);

    List<TransactionEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);
    List<TransactionEntity> findByPenaltyCaseIdOrderByCreatedAtAsc(Long penaltyCaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entry from TransactionEntity entry
            where entry.userId = :userId
              and entry.amount > coalesce(entry.paidAmount, 0)
              and entry.status in ('OUTSTANDING', 'PARTIALLY_PAID', 'PARTIALLY_ADJUSTED', 'PARTIALLY_WAIVED')
            order by entry.createdAt, entry.transactionId
            """)
    List<TransactionEntity> findPayableByUserIdForUpdate(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from TransactionEntity entry where entry.transactionId = :transactionId")
    Optional<TransactionEntity> findByIdForUpdate(@Param("transactionId") Long transactionId);
}
