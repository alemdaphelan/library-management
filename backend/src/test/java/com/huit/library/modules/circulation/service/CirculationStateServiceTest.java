package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.dto.LoanStateResponseDTO;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanStatusHistoryEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanStatusHistoryRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.circulation.repository.CirculationOperationRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.penalty.service.LateFineService;
import com.huit.library.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CirculationStateServiceTest {
    private LoanTicketRepository ticketRepository;
    private LoanDetailRepository detailRepository;
    private LoanStatusHistoryRepository historyRepository;
    private BookCopyRepository copyRepository;
    private CirculationStateService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(LoanTicketRepository.class);
        detailRepository = mock(LoanDetailRepository.class);
        historyRepository = mock(LoanStatusHistoryRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        service = new CirculationStateService(
                ticketRepository,
                detailRepository,
                historyRepository,
                copyRepository,
                mock(UserRepository.class),
                mock(WalletRepository.class),
                mock(CirculationOperationRepository.class),
                new CirculationStateMachine(),
                mock(LateFineService.class),
                mock(WaitlistService.class),
                mock(NotificationService.class));
    }

    @Test
    void approveMovesPendingReservationAndWritesAuditHistory() {
        UUID librarianId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(10L, "PENDING");
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));

        LoanStateResponseDTO result = service.approveReservation(10L, librarianId);

        assertThat(result.getPreviousStatus()).isEqualTo(LoanTicketStatus.PENDING);
        assertThat(result.getStatus()).isEqualTo(LoanTicketStatus.RESERVED);
        assertThat(ticket.getStatus()).isEqualTo("RESERVED");
        assertThat(ticket.getProcessedBy()).isEqualTo(librarianId);

        ArgumentCaptor<LoanStatusHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(LoanStatusHistoryEntity.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getTicketId()).isEqualTo(10L);
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(LoanTicketStatus.PENDING);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(LoanTicketStatus.RESERVED);
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo(librarianId);
    }

    @Test
    void rejectReleasesReservedCopiesAndCancelsDetails() {
        UUID librarianId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(11L, "RESERVED");
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(11L);
        detail.setBarcode("BC-011");
        detail.setReturnStatus("PENDING");
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-011");
        copy.setStatus("RESERVED");
        copy.setLockedByUser(UUID.randomUUID());

        when(ticketRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ticket));
        when(detailRepository.findByTicketId(11L)).thenReturn(List.of(detail));
        when(copyRepository.findById("BC-011")).thenReturn(Optional.of(copy));

        LoanStateResponseDTO result = service.rejectReservation(11L, librarianId, "Thẻ thư viện hết hạn");

        assertThat(result.getStatus()).isEqualTo(LoanTicketStatus.REJECTED);
        assertThat(detail.getReturnStatus()).isEqualTo("CANCELLED");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        assertThat(copy.getLockedByUser()).isNull();
        verify(detailRepository).save(detail);
        verify(copyRepository).save(copy);
    }

    @Test
    void cannotApproveBorrowingTicket() {
        LoanTicketEntity ticket = ticket(12L, "BORROWING");
        when(ticketRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.approveReservation(12L, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BORROWING")
                .hasMessageContaining("RESERVED");
        verify(ticketRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void missingTicketReturnsNotFound() {
        when(ticketRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveReservation(404L, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private LoanTicketEntity ticket(Long id, String status) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(id);
        ticket.setStatus(status);
        return ticket;
    }
}
