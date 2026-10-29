package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.CirculationOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CirculationOperationRepository extends JpaRepository<CirculationOperationEntity, UUID> {
    Optional<CirculationOperationEntity> findByIdempotencyKey(String idempotencyKey);
}
