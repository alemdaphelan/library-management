package com.huit.library.modules.inventory.repository;

import com.huit.library.modules.inventory.entity.InventoryAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryAuditRepository extends JpaRepository<InventoryAuditEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select a from InventoryAuditEntity a where a.auditId = :id")
    java.util.Optional<InventoryAuditEntity> findForDisposal(@org.springframework.data.repository.query.Param("id") Long id);
    List<InventoryAuditEntity> findAllByOrderByCreatedAtDesc();
}
