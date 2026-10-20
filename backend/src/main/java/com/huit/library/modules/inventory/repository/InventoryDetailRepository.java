package com.huit.library.modules.inventory.repository;

import com.huit.library.modules.inventory.entity.InventoryDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryDetailRepository extends JpaRepository<InventoryDetailEntity, String> {
    List<InventoryDetailEntity> findByAuditIdOrderByCreatedAtAsc(String auditId);
    Optional<InventoryDetailEntity> findByAuditIdAndBarcode(String auditId, String barcode);
}
