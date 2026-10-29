package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.WaitlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntity, Long> {
    boolean existsByBookIdAndUserIdAndStatus(Long bookId, UUID userId, String status);
    List<WaitlistEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<WaitlistEntity> findByBookIdAndStatus(Long bookId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WaitlistEntity> findFirstByBookIdAndStatusOrderByCreatedAtAsc(Long bookId, String status);
    Optional<WaitlistEntity> findFirstByBookIdAndUserIdAndStatus(Long bookId, UUID userId, String status);
    List<WaitlistEntity> findByStatusAndExpiresAtBefore(String status, LocalDateTime expiresAt);
}
