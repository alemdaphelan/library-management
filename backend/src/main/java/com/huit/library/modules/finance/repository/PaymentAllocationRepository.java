package com.huit.library.modules.finance.repository;

import com.huit.library.modules.finance.entity.PaymentAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocationEntity, Long> {
    List<PaymentAllocationEntity> findByPaymentTransactionIdOrderByAllocationId(Long paymentTransactionId);
}
