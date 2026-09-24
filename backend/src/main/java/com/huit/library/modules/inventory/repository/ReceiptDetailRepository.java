package com.huit.library.modules.inventory.repository;

import com.huit.library.modules.inventory.entity.ReceiptDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptDetailRepository extends JpaRepository<ReceiptDetailEntity, Long> {
}
