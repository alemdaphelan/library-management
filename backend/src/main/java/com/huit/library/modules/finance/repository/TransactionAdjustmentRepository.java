package com.huit.library.modules.finance.repository;

import com.huit.library.modules.finance.entity.TransactionAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionAdjustmentRepository extends JpaRepository<TransactionAdjustmentEntity, Long> {
    List<TransactionAdjustmentEntity> findAllByOrderByCreatedAtDesc();
    boolean existsByTransactionIdAndStatus(Long transactionId, String status);
}
