package com.huit.library.modules.inventory.repository;
import com.huit.library.modules.inventory.entity.DisposalProposalEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface DisposalProposalRepository extends JpaRepository<DisposalProposalEntity, UUID>, JpaSpecificationExecutor<DisposalProposalEntity> {
    List<DisposalProposalEntity> findByRequestIdOrderByBarcode(UUID requestId);
    List<DisposalProposalEntity> findByBarcodeInAndStatusIn(Collection<String> barcodes, Collection<String> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DisposalProposalEntity p where p.proposalId = :id")
    Optional<DisposalProposalEntity> findForUpdate(@Param("id") UUID id);
}
