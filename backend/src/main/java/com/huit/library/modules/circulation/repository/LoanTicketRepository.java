package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanTicketRepository extends JpaRepository<LoanTicketEntity, Long> {
    List<LoanTicketEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<LoanTicketEntity> findByUserIdAndStatus(UUID userId, String status);
}
