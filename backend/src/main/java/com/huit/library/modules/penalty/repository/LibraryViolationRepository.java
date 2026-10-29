package com.huit.library.modules.penalty.repository;

import com.huit.library.modules.penalty.entity.LibraryViolationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LibraryViolationRepository extends JpaRepository<LibraryViolationEntity, Long> {
    List<LibraryViolationEntity> findByUserIdOrderByOccurredAtDesc(UUID userId);
}
