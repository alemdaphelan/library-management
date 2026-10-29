package com.huit.library.modules.interactive.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import com.huit.library.modules.interactive.entity.WishlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {
    List<WishlistEntity> findByUserIdOrderByAddedAtDesc(UUID userId);
    Optional<WishlistEntity> findByUserIdAndBookId(UUID userId, String bookId);
    boolean existsByUserIdAndBookId(UUID userId, String bookId);
    long deleteByUserIdAndBookId(UUID userId, String bookId);
}
