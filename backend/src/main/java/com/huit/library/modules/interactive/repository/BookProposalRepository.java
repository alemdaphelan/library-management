package com.huit.library.modules.interactive.repository;

import com.huit.library.modules.interactive.entity.BookProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookProposalRepository extends JpaRepository<BookProposalEntity, Long> {
}
