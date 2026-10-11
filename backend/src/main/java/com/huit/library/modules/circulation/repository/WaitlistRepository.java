package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.WaitlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntity, Long> {
    boolean existsByBookIdAndUserIdAndStatus(Long bookId, UUID userId, String status);
    List<WaitlistEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<WaitlistEntity> findByBookIdAndStatus(Long bookId, String status);
}
