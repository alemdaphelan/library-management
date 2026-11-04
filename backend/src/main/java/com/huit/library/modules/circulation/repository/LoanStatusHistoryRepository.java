package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanStatusHistoryEntity;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanStatusHistoryRepository extends JpaRepository<LoanStatusHistoryEntity, Long> {
    List<LoanStatusHistoryEntity> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    Optional<LoanStatusHistoryEntity> findFirstByTicketIdAndToStatusOrderByCreatedAtAsc(
            Long ticketId,
            LoanTicketStatus toStatus);
}
