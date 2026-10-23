package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanStatusHistoryEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanStatusHistoryRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.penalty.service.LateFineService;
import com.huit.library.modules.penalty.service.OverdueViolationService;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CirculationMaintenanceServiceTest {
    private LoanTicketRepository ticketRepository;
    private LoanDetailRepository detailRepository;
    private LoanStatusHistoryRepository historyRepository;
    private BookCopyRepository copyRepository;
    private LateFineService lateFineService;
    private NotificationService notificationService;
    private ReservationNoShowService reservationNoShowService;
    private CirculationMaintenanceService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(LoanTicketRepository.class);
        detailRepository = mock(LoanDetailRepository.class);
        historyRepository = mock(LoanStatusHistoryRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        lateFineService = mock(LateFineService.class);
        notificationService = mock(NotificationService.class);
        reservationNoShowService = mock(ReservationNoShowService.class);
        service = new CirculationMaintenanceService(
                ticketRepository,
                detailRepository,
                historyRepository,
                copyRepository,
                new CirculationStateMachine(),
                lateFineService,
                notificationService,
                mock(OverdueViolationService.class),
                reservationNoShowService,
                mock(WaitlistService.class),
                mock(PenaltyCaseRepository.class),
                2);
        now = LocalDateTime.of(2026, 10, 17, 12, 0);
        when(ticketRepository.findExpiredReservationIds(now)).thenReturn(List.of());
        when(ticketRepository.findOverdueTicketIds(now)).thenReturn(List.of());
        when(detailRepository.findDueSoon(now, now.plusDays(2))).thenReturn(List.of());
    }

    @Test
    void expiresReservationAndReleasesItsCopy() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(10L, readerId, "RESERVED");
        LoanDetailEntity detail = detail(20L, 10L, "BC-010", "PENDING");
        BookCopyEntity copy = copy("BC-010", "RESERVED", readerId, now.minusMinutes(1));

        when(ticketRepository.findExpiredReservationIds(now)).thenReturn(List.of(10L));
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(detailRepository.findByTicketIdForUpdate(10L)).thenReturn(List.of(detail));
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-010"))).thenReturn(List.of(copy));

        CirculationMaintenanceResult result = service.processAt(now);

        assertThat(result.expiredReservations()).isEqualTo(1);
        assertThat(ticket.getStatus()).isEqualTo("CANCELLED");
        assertThat(detail.getReturnStatus()).isEqualTo("CANCELLED");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        assertThat(copy.getLockedByUser()).isNull();
        assertThat(copy.getLockedUntil()).isNull();
        verify(notificationService).createOnce(
                eq(readerId), eq("RESERVATION_EXPIRED"), anyString(),
                eq("LOAN_TICKET"), eq("10"), eq("RESERVATION_EXPIRED:10"), eq(now));
        verify(reservationNoShowService).record(readerId, now, "LOAN_TICKET", "10");

        ArgumentCaptor<LoanStatusHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(LoanStatusHistoryEntity.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getChangedBy()).isNull();
    }

    @Test
    void marksTicketDetailAndCopyOverdueAndAccruesFine() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(11L, readerId, "BORROWING");
        LoanDetailEntity detail = detail(21L, 11L, "BC-011", "BORROWING");
        detail.setDueDate(now.minusDays(1).minusHours(2));
        BookCopyEntity copy = copy("BC-011", "BORROWING", null, null);

        when(ticketRepository.findOverdueTicketIds(now)).thenReturn(List.of(11L));
        when(ticketRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ticket));
        when(detailRepository.findOverdueByTicketIdForUpdate(11L, now)).thenReturn(List.of(detail));
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-011"))).thenReturn(List.of(copy));
        when(lateFineService.accrueLateFine(ticket, detail, now)).thenReturn(new BigDecimal("1000"));

        CirculationMaintenanceResult result = service.processAt(now);

        assertThat(result.overdueTickets()).isEqualTo(1);
        assertThat(ticket.getStatus()).isEqualTo("OVERDUE");
        assertThat(detail.getReturnStatus()).isEqualTo("OVERDUE");
        assertThat(copy.getStatus()).isEqualTo("OVERDUE");
        verify(lateFineService).accrueLateFine(ticket, detail, now);
        verify(notificationService).createOnce(
                eq(readerId), eq("LOAN_OVERDUE"), anyString(),
                eq("LOAN_DETAIL"), eq("21"), eq("LOAN_OVERDUE:21:DAY:1"), eq(now));
    }

    @Test
    void createsOnlyIdempotentDueSoonNotifications() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(12L, readerId, "BORROWING");
        LoanDetailEntity detail = detail(22L, 12L, "BC-012", "BORROWING");
        detail.setDueDate(now.plusDays(1));

        when(detailRepository.findDueSoon(now, now.plusDays(2))).thenReturn(List.of(detail));
        when(ticketRepository.findById(12L)).thenReturn(Optional.of(ticket));
        when(notificationService.createOnce(
                eq(readerId), eq("LOAN_DUE_SOON"), anyString(),
                eq("LOAN_DETAIL"), eq("22"), anyString(), eq(now)))
                .thenReturn(true);

        CirculationMaintenanceResult result = service.processAt(now);

        assertThat(result.dueSoonNotifications()).isEqualTo(1);
    }

    private LoanTicketEntity ticket(Long id, UUID userId, String status) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(id);
        ticket.setUserId(userId);
        ticket.setStatus(status);
        return ticket;
    }

    private LoanDetailEntity detail(
            Long detailId, Long ticketId, String barcode, String status) {
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setDetailId(detailId);
        detail.setTicketId(ticketId);
        detail.setBarcode(barcode);
        detail.setReturnStatus(status);
        return detail;
    }

    private BookCopyEntity copy(
            String barcode,
            String status,
            UUID lockedBy,
            LocalDateTime lockedUntil) {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode(barcode);
        copy.setStatus(status);
        copy.setLockedByUser(lockedBy);
        copy.setLockedUntil(lockedUntil);
        return copy;
    }
}
