package com.huit.library.modules.interactive.repository;

import java.util.UUID;

import com.huit.library.modules.interactive.entity.WishlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {
}
