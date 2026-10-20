package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanStatusHistoryRepository extends JpaRepository<LoanStatusHistoryEntity, Long> {
    List<LoanStatusHistoryEntity> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
