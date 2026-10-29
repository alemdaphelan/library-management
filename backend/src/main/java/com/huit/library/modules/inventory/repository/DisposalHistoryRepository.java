package com.huit.library.modules.inventory.repository;
import com.huit.library.modules.inventory.entity.DisposalHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DisposalHistoryRepository extends JpaRepository<DisposalHistoryEntity, UUID> {
    List<DisposalHistoryEntity> findByProposalIdOrderByProposalVersion(UUID proposalId);
}
