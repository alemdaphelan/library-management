package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface LoanTicketRepository extends JpaRepository<LoanTicketEntity, Long> {
    List<LoanTicketEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<LoanTicketEntity> findByUserIdAndStatus(UUID userId, String status);
    List<LoanTicketEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);
    List<LoanTicketEntity> findTop10ByOrderByUpdatedAtDesc();
    List<LoanTicketEntity> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from LoanTicketEntity ticket where ticket.ticketId = :ticketId")
    java.util.Optional<LoanTicketEntity> findByIdForUpdate(@Param("ticketId") Long ticketId);

    @Query("""
            select distinct ticket.ticketId
            from LoanTicketEntity ticket, LoanDetailEntity detail, BookCopyEntity copy
            where detail.ticketId = ticket.ticketId
              and detail.barcode = copy.barcode
              and ticket.status in ('PENDING', 'RESERVED')
              and detail.returnStatus = 'PENDING'
              and copy.status = 'RESERVED'
              and copy.lockedUntil is not null
              and copy.lockedUntil < :now
            order by ticket.ticketId
            """)
    List<Long> findExpiredReservationIds(@Param("now") LocalDateTime now);

    @Query("""
            select distinct ticket.ticketId
            from LoanTicketEntity ticket, LoanDetailEntity detail
            where detail.ticketId = ticket.ticketId
              and ticket.status in ('BORROWING', 'OVERDUE')
              and detail.returnStatus in ('BORROWING', 'OVERDUE')
              and detail.returnDate is null
              and detail.dueDate < :now
            order by ticket.ticketId
            """)
    List<Long> findOverdueTicketIds(@Param("now") LocalDateTime now);
}
