package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository
public interface LoanDetailRepository extends JpaRepository<LoanDetailEntity, Long> {
    List<LoanDetailEntity> findByTicketId(Long ticketId);
    Optional<LoanDetailEntity> findFirstByTicketId(Long ticketId);
    Optional<LoanDetailEntity> findFirstByBarcodeAndReturnDateIsNull(String barcode);
    long countByReturnDateIsNullAndDueDateBefore(java.time.LocalDateTime dueDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select detail from LoanDetailEntity detail where detail.detailId = :detailId")
    Optional<LoanDetailEntity> findByIdForUpdate(@Param("detailId") Long detailId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select detail from LoanDetailEntity detail where detail.ticketId = :ticketId order by detail.detailId")
    List<LoanDetailEntity> findByTicketIdForUpdate(@Param("ticketId") Long ticketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select detail from LoanDetailEntity detail
            where detail.ticketId = :ticketId
              and detail.returnDate is null
              and detail.returnStatus in ('BORROWING', 'OVERDUE')
              and detail.dueDate < :now
            order by detail.detailId
            """)
    List<LoanDetailEntity> findOverdueByTicketIdForUpdate(
            @Param("ticketId") Long ticketId,
            @Param("now") LocalDateTime now);

    @Query("""
            select detail from LoanDetailEntity detail
            where detail.returnDate is null
              and detail.returnStatus = 'BORROWING'
              and detail.dueDate >= :from
              and detail.dueDate < :until
            order by detail.dueDate, detail.detailId
            """)
    List<LoanDetailEntity> findDueSoon(
            @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select detail from LoanDetailEntity detail
            where detail.barcode in :barcodes
              and detail.returnDate is null
              and detail.returnStatus in ('BORROWING', 'OVERDUE')
            order by detail.barcode
            """)
    List<LoanDetailEntity> findActiveByBarcodesForUpdate(@Param("barcodes") Collection<String> barcodes);

    @Query("""
            select detail from LoanDetailEntity detail
            where detail.barcode = :barcode
              and detail.returnDate is null
              and detail.returnStatus in ('BORROWING', 'OVERDUE')
            """)
    Optional<LoanDetailEntity> findActiveByBarcode(@Param("barcode") String barcode);

    @Query("""
            select count(detail) from LoanDetailEntity detail, LoanTicketEntity ticket
            where detail.ticketId = ticket.ticketId
              and ticket.userId = :userId
              and ticket.status in :statuses
              and detail.returnStatus <> 'CANCELLED'
              and detail.returnDate is null
            """)
    long countOpenItemsByUserId(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<String> statuses);

    @Query(value = """
            SELECT COUNT(ld.detail_id)
            FROM loan_details ld
            JOIN loan_tickets lt ON lt.ticket_id = ld.ticket_id
            JOIN book_copies bc ON bc.barcode = ld.barcode
            WHERE lt.user_id = :userId
              AND bc.book_id = :bookId
              AND ld.return_date IS NULL
              AND COALESCE(ld.return_status, '') IN ('PENDING', 'RESERVED', 'BORROWING', 'OVERDUE')
              AND COALESCE(lt.status, '') IN ('PENDING', 'RESERVED', 'BORROWING', 'OVERDUE')
            """, nativeQuery = true)
    long countOpenItemsByUserIdAndBookId(
            @Param("userId") UUID userId,
            @Param("bookId") Long bookId);

    @Query("""
            select count(detail) from LoanDetailEntity detail, LoanTicketEntity ticket
            where detail.ticketId = ticket.ticketId
              and ticket.userId = :userId
              and detail.returnDate is null
              and (ticket.status = 'OVERDUE'
                   or (ticket.status = 'BORROWING' and detail.dueDate < :now))
            """)
    long countOverdueItemsByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT COUNT(ld.detail_id)
            FROM loan_details ld
            JOIN loan_tickets lt ON lt.ticket_id = ld.ticket_id
            JOIN book_copies bc ON bc.barcode = ld.barcode
            WHERE bc.book_id = :bookId
              AND COALESCE(ld.created_at, lt.created_at) >= :since
              AND (
                    ld.return_status IN ('BORROWING', 'OVERDUE', 'RETURNED', 'LOST', 'DAMAGED')
                    OR ld.return_date IS NOT NULL
                  )
              AND COALESCE(lt.status, '') NOT IN ('CANCELLED', 'REJECTED')
            """, nativeQuery = true)
    long countBorrowedByBookIdSince(
            @Param("bookId") Long bookId,
            @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT b.book_id AS bookId, b.title AS title, b.author AS author,
                   COUNT(ld.detail_id) AS borrows
            FROM loan_details ld
            JOIN book_copies bc ON bc.barcode = ld.barcode
            JOIN books b ON b.book_id = bc.book_id
            WHERE COALESCE(ld.return_status, '') <> 'CANCELLED'
            GROUP BY b.book_id, b.title, b.author
            ORDER BY COUNT(ld.detail_id) DESC, b.title ASC
            """, nativeQuery = true)
    List<TrendingBookProjection> findTrendingBooks(Pageable pageable);

    @Query(value = """
            SELECT b.book_id AS bookId,
                   COUNT(ld.detail_id) AS borrowCount,
                   SUM(EXP(
                       -0.6931471805599453
                       * GREATEST(EXTRACT(EPOCH FROM (:asOf - COALESCE(ld.created_at, lt.created_at))) / 86400.0, 0)
                       / :halfLifeDays
                   )) AS weightedScore,
                   MAX(COALESCE(ld.created_at, lt.created_at)) AS lastBorrowedAt
            FROM loan_details ld
            JOIN loan_tickets lt ON lt.ticket_id = ld.ticket_id
            JOIN book_copies bc ON bc.barcode = ld.barcode
            JOIN books b ON b.book_id = bc.book_id
            WHERE COALESCE(ld.created_at, lt.created_at) >= :since
              AND (
                    ld.return_status IN ('BORROWING', 'OVERDUE', 'RETURNED', 'LOST', 'DAMAGED')
                    OR ld.return_date IS NOT NULL
                  )
              AND COALESCE(lt.status, '') NOT IN ('CANCELLED', 'REJECTED')
            GROUP BY b.book_id
            ORDER BY weightedScore DESC, borrowCount DESC, b.book_id ASC
            """, nativeQuery = true)
    List<BookPopularityProjection> findPopularityScores(
            @Param("since") LocalDateTime since,
            @Param("asOf") LocalDateTime asOf,
            @Param("halfLifeDays") double halfLifeDays,
            Pageable pageable);

    @Query(value = """
            SELECT b.book_id AS bookId,
                   COUNT(ld.detail_id) AS borrowCount,
                   COALESCE(SUM(ld.renewal_count), 0) AS renewalCount,
                   MAX(COALESCE(ld.return_date, ld.updated_at, ld.created_at, lt.updated_at, lt.created_at)) AS lastInteractedAt
            FROM loan_tickets lt
            JOIN loan_details ld ON ld.ticket_id = lt.ticket_id
            JOIN book_copies bc ON bc.barcode = ld.barcode
            JOIN books b ON b.book_id = bc.book_id
            WHERE lt.user_id = :userId
              AND (
                    ld.return_status IN ('BORROWING', 'OVERDUE', 'RETURNED', 'LOST', 'DAMAGED')
                    OR ld.return_date IS NOT NULL
                  )
              AND COALESCE(lt.status, '') NOT IN ('CANCELLED', 'REJECTED')
            GROUP BY b.book_id
            ORDER BY lastInteractedAt DESC, b.book_id ASC
            """, nativeQuery = true)
    List<UserBookInteractionProjection> findUserBookInteractions(
            @Param("userId") UUID userId,
            Pageable pageable);

    @Query(value = """
            SELECT lt.user_id AS userId,
                   b.book_id AS bookId,
                   COALESCE(ld.return_date, ld.updated_at, ld.created_at, lt.updated_at, lt.created_at) AS interactionAt
            FROM loan_tickets lt
            JOIN loan_details ld ON ld.ticket_id = lt.ticket_id
            JOIN book_copies bc ON bc.barcode = ld.barcode
            JOIN books b ON b.book_id = bc.book_id
            WHERE (
                    ld.return_status IN ('BORROWING', 'OVERDUE', 'RETURNED', 'LOST', 'DAMAGED')
                    OR ld.return_date IS NOT NULL
                  )
              AND COALESCE(lt.status, '') NOT IN ('CANCELLED', 'REJECTED')
            ORDER BY lt.user_id, interactionAt, ld.detail_id
            """, nativeQuery = true)
    List<EvaluationInteractionProjection> findEvaluationInteractions();
}
