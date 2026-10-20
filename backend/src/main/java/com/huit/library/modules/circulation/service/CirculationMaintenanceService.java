package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanStatusHistoryEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.model.LoanDetailStatus;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanStatusHistoryRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.penalty.service.LateFineService;
import com.huit.library.modules.penalty.service.OverdueViolationService;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.time.temporal.ChronoUnit;

@Service
public class CirculationMaintenanceService {
    private final LoanTicketRepository loanTicketRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final LoanStatusHistoryRepository historyRepository;
    private final BookCopyRepository bookCopyRepository;
    private final CirculationStateMachine stateMachine;
    private final LateFineService lateFineService;
    private final NotificationService notificationService;
    private final OverdueViolationService overdueViolationService;
    private final WaitlistService waitlistService;
    private final PenaltyCaseRepository penaltyCaseRepository;
    private final int dueSoonDays;

    public CirculationMaintenanceService(
            LoanTicketRepository loanTicketRepository,
            LoanDetailRepository loanDetailRepository,
            LoanStatusHistoryRepository historyRepository,
            BookCopyRepository bookCopyRepository,
            CirculationStateMachine stateMachine,
            LateFineService lateFineService,
            NotificationService notificationService,
            OverdueViolationService overdueViolationService,
            WaitlistService waitlistService,
            PenaltyCaseRepository penaltyCaseRepository,
            @Value("${library.circulation.due-soon-days:2}") int dueSoonDays) {
        this.loanTicketRepository = loanTicketRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.historyRepository = historyRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.stateMachine = stateMachine;
        this.lateFineService = lateFineService;
        this.notificationService = notificationService;
        this.overdueViolationService = overdueViolationService;
        this.waitlistService = waitlistService;
        this.penaltyCaseRepository = penaltyCaseRepository;
        this.dueSoonDays = dueSoonDays;
    }

    @Transactional
    public CirculationMaintenanceResult processAt(LocalDateTime now) {
        waitlistService.expireAndPromote(now);
        int expiredReservations = 0;
        for (Long ticketId : loanTicketRepository.findExpiredReservationIds(now)) {
            if (expireReservation(ticketId, now)) {
                expiredReservations++;
            }
        }

        int overdueTickets = 0;
        for (Long ticketId : loanTicketRepository.findOverdueTicketIds(now)) {
            if (markOverdueAndAccrue(ticketId, now)) {
                overdueTickets++;
            }
        }

        int dueSoonNotifications = createDueSoonNotifications(now) + createPenaltyCaseReminders(now);
        return new CirculationMaintenanceResult(
                expiredReservations, overdueTickets, dueSoonNotifications);
    }

    private boolean expireReservation(Long ticketId, LocalDateTime now) {
        LoanTicketEntity ticket = loanTicketRepository.findByIdForUpdate(ticketId).orElse(null);
        if (ticket == null) {
            return false;
        }
        LoanTicketStatus current = LoanTicketStatus.fromDatabaseValue(ticket.getStatus());
        if (current != LoanTicketStatus.PENDING && current != LoanTicketStatus.RESERVED) {
            return false;
        }

        List<LoanDetailEntity> details = loanDetailRepository.findByTicketIdForUpdate(ticketId);
        List<String> barcodes = details.stream()
                .map(LoanDetailEntity::getBarcode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<BookCopyEntity> copies = barcodes.isEmpty()
                ? List.of()
                : bookCopyRepository.findAllByBarcodeInForUpdate(barcodes);
        boolean hasExpiredCopy = copies.stream().anyMatch(copy ->
                "RESERVED".equalsIgnoreCase(copy.getStatus())
                        && copy.getLockedUntil() != null
                        && copy.getLockedUntil().isBefore(now));
        if (!hasExpiredCopy) {
            return false;
        }

        transition(ticket, LoanTicketStatus.CANCELLED, "Tự động hủy do quá hạn nhận sách");
        for (LoanDetailEntity detail : details) {
            if (LoanDetailStatus.fromDatabaseValue(detail.getReturnStatus()) == LoanDetailStatus.PENDING) {
                detail.setReturnStatus(LoanDetailStatus.CANCELLED.name());
                loanDetailRepository.save(detail);
            }
        }
        for (BookCopyEntity copy : copies) {
            if ("RESERVED".equalsIgnoreCase(copy.getStatus())
                    && Objects.equals(copy.getLockedByUser(), ticket.getUserId())) {
                copy.setStatus("AVAILABLE");
                copy.setLockedByUser(null);
                copy.setLockedUntil(null);
                bookCopyRepository.save(copy);
            }
        }
        notificationService.createOnce(
                ticket.getUserId(), "RESERVATION_EXPIRED",
                "Phiếu #" + ticketId + " đã tự hủy vì quá hạn nhận sách.",
                "LOAN_TICKET", ticketId.toString(),
                "RESERVATION_EXPIRED:" + ticketId, now);
        return true;
    }

    private boolean markOverdueAndAccrue(Long ticketId, LocalDateTime now) {
        LoanTicketEntity ticket = loanTicketRepository.findByIdForUpdate(ticketId).orElse(null);
        if (ticket == null) {
            return false;
        }
        LoanTicketStatus current = LoanTicketStatus.fromDatabaseValue(ticket.getStatus());
        if (current != LoanTicketStatus.BORROWING && current != LoanTicketStatus.OVERDUE) {
            return false;
        }

        List<LoanDetailEntity> overdueDetails =
                loanDetailRepository.findOverdueByTicketIdForUpdate(ticketId, now);
        if (overdueDetails.isEmpty()) {
            return false;
        }
        if (current == LoanTicketStatus.BORROWING) {
            transition(ticket, LoanTicketStatus.OVERDUE, "Tự động đánh dấu phiếu quá hạn");
            overdueViolationService.recordFirstOverdue(ticket, now);
        }

        List<String> barcodes = overdueDetails.stream()
                .map(LoanDetailEntity::getBarcode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<BookCopyEntity> copies = barcodes.isEmpty()
                ? List.of()
                : bookCopyRepository.findAllByBarcodeInForUpdate(barcodes);
        for (LoanDetailEntity detail : overdueDetails) {
            detail.setReturnStatus(LoanDetailStatus.OVERDUE.name());
            loanDetailRepository.save(detail);
            lateFineService.accrueLateFine(ticket, detail, now);
            long overdueDay = ChronoUnit.DAYS.between(
                    detail.getDueDate().toLocalDate(), now.toLocalDate());
            if (shouldNotifyOverdue(overdueDay)) {
                notificationService.createOnce(
                        ticket.getUserId(), "LOAN_OVERDUE",
                        "Tài liệu " + detail.getBarcode() + " của phiếu #" + ticketId
                                + " đã quá hạn " + overdueDay
                                + " ngày và đã phát sinh tiền phạt.",
                        "LOAN_DETAIL", detail.getDetailId().toString(),
                        "LOAN_OVERDUE:" + detail.getDetailId() + ":DAY:" + overdueDay, now);
            }
        }
        for (BookCopyEntity copy : copies) {
            if ("BORROWING".equalsIgnoreCase(copy.getStatus())) {
                copy.setStatus("OVERDUE");
                bookCopyRepository.save(copy);
            }
        }
        return true;
    }

    private int createDueSoonNotifications(LocalDateTime now) {
        int created = 0;
        LocalDateTime until = now.plusDays(dueSoonDays);
        for (LoanDetailEntity detail : loanDetailRepository.findDueSoon(now, until)) {
            LoanTicketEntity ticket = loanTicketRepository.findById(detail.getTicketId()).orElse(null);
            if (ticket == null || ticket.getUserId() == null) {
                continue;
            }
            String dueKey = detail.getDueDate().toString();
            if (notificationService.createOnce(
                    ticket.getUserId(), "LOAN_DUE_SOON",
                    "Tài liệu " + detail.getBarcode() + " sẽ đến hạn vào " + detail.getDueDate() + ".",
                    "LOAN_DETAIL", detail.getDetailId().toString(),
                    "LOAN_DUE_SOON:" + detail.getDetailId() + ":" + dueKey, now)) {
                created++;
            }
        }
        return created;
    }

    private int createPenaltyCaseReminders(LocalDateTime now) {
        int created = 0;
        for (PenaltyCaseEntity penaltyCase : penaltyCaseRepository.findByStatusIn(
                List.of("OUTSTANDING", "WAITING_REPLACEMENT"))) {
            long day = ChronoUnit.DAYS.between(
                    penaltyCase.getReportedAt().toLocalDate(), now.toLocalDate());
            if (day != 20 && day != 25 && day != 29) {
                continue;
            }
            if (notificationService.createOnce(
                    penaltyCase.getUserId(), "PENALTY_CASE_DUE",
                    "Hồ sơ đền bù #" + penaltyCase.getCaseId()
                            + " phải hoàn tất trước " + penaltyCase.getSettlementDueAt().toLocalDate() + ".",
                    "PENALTY_CASE", penaltyCase.getCaseId().toString(),
                    "PENALTY_CASE_DUE:" + penaltyCase.getCaseId() + ":DAY:" + day, now)) {
                created++;
            }
        }
        return created;
    }

    private boolean shouldNotifyOverdue(long overdueDay) {
        return overdueDay == 1 || overdueDay == 3 || overdueDay == 7
                || (overdueDay > 7 && overdueDay % 7 == 0);
    }

    private void transition(
            LoanTicketEntity ticket,
            LoanTicketStatus target,
            String reason) {
        LoanTicketStatus previous = stateMachine.transition(ticket, target);
        ticket.setDecisionNote(reason);
        loanTicketRepository.save(ticket);

        LoanStatusHistoryEntity history = new LoanStatusHistoryEntity();
        history.setTicketId(ticket.getTicketId());
        history.setFromStatus(previous);
        history.setToStatus(target);
        history.setReason(reason);
        historyRepository.save(history);
    }
}
