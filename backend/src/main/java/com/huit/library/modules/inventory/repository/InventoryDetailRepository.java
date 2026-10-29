package com.huit.library.modules.inventory.repository;

import com.huit.library.modules.inventory.entity.InventoryDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryDetailRepository extends JpaRepository<InventoryDetailEntity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select d from InventoryDetailEntity d where d.auditId = :auditId order by d.barcode, d.detailId")
    List<InventoryDetailEntity> findForDisposal(@org.springframework.data.repository.query.Param("auditId") String auditId);
    List<InventoryDetailEntity> findByAuditIdOrderByCreatedAtAsc(String auditId);
    Optional<InventoryDetailEntity> findByAuditIdAndBarcode(String auditId, String barcode);
}
