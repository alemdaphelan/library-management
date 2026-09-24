package com.huit.library.modules.inventory.repository;

import com.huit.library.modules.inventory.entity.GoodsReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceiptEntity, Long> {
}
