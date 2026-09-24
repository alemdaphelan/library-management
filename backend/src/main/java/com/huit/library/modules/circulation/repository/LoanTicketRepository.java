package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanTicketRepository extends JpaRepository<LoanTicketEntity, Long> {
}
