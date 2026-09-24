package com.huit.library.modules.inventory.repository;

import com.huit.library.modules.inventory.entity.InventoryAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryAuditRepository extends JpaRepository<InventoryAuditEntity, Long> {
}
