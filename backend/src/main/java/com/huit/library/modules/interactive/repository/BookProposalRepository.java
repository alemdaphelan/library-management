package com.huit.library.modules.interactive.repository;

import com.huit.library.modules.interactive.entity.BookProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookProposalRepository extends JpaRepository<BookProposalEntity, Long> {
    List<BookProposalEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<BookProposalEntity> findAllByOrderByCreatedAtDesc();
}
