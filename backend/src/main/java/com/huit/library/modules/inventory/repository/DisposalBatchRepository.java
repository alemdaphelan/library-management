package com.huit.library.modules.inventory.repository;
import com.huit.library.modules.inventory.entity.DisposalBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface DisposalBatchRepository extends JpaRepository<DisposalBatchEntity, UUID> {}
