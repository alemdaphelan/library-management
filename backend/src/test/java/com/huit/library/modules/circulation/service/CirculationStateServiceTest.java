package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.dto.LoanStateResponseDTO;
import com.huit.library.modules.circulation.dto.ManualCheckoutRequestDTO;
import com.huit.library.modules.circulation.entity.CirculationOperationEntity;
import com.huit.library.modules.auth.entity.UserEntity;
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
    private WaitlistService waitlistService;
    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private CirculationOperationRepository operationRepository;
    private CirculationStateService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(LoanTicketRepository.class);
        detailRepository = mock(LoanDetailRepository.class);
        historyRepository = mock(LoanStatusHistoryRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        waitlistService = mock(WaitlistService.class);
        userRepository = mock(UserRepository.class);
        walletRepository = mock(WalletRepository.class);
        operationRepository = mock(CirculationOperationRepository.class);
        service = new CirculationStateService(
                ticketRepository,
                detailRepository,
                historyRepository,
                copyRepository,
                userRepository,
                walletRepository,
                operationRepository,
                new CirculationStateMachine(),
                mock(LateFineService.class),
                waitlistService,
                mock(NotificationService.class));
    }

    @Test
    void approveMovesPendingReservationAndWritesAuditHistory() {
        UUID librarianId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(10L, "PENDING");
        ticket.setUserId(readerId);
        UserEntity reader = new UserEntity();
        reader.setUserId(readerId);
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(reader));
        when(detailRepository.countOpenItemsByUserId(eq(readerId), any())).thenReturn(1L);

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
    void eligibilityCountsAllOpenItemsAndExplainsWhenLimitIsReached() {
        UUID readerId = UUID.randomUUID();
        when(detailRepository.countOpenItemsByUserId(eq(readerId), any())).thenReturn(3L);

        var result = service.getBorrowEligibility(readerId);

        assertThat(result.getOpenItems()).isEqualTo(3);
        assertThat(result.getRemainingSlots()).isZero();
        assertThat(result.isEligible()).isFalse();
        assertThat(result.getMessage()).contains("đủ 3 tài liệu");
    }

    @Test
    void approvalIsRejectedWhenReaderAlreadyHasMoreThanThreeOpenItems() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(13L, "PENDING");
        ticket.setUserId(readerId);
        UserEntity reader = new UserEntity();
        reader.setUserId(readerId);
        when(ticketRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(reader));
        when(detailRepository.countOpenItemsByUserId(eq(readerId), any())).thenReturn(4L);

        assertThatThrownBy(() -> service.approveReservation(13L, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vượt giới hạn 3 tài liệu");
        verify(ticketRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void rejectReleasesReservedCopiesAndCancelsDetails() {
        UUID librarianId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(11L, "RESERVED");
        ticket.setUserId(readerId);
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(11L);
        detail.setBarcode("BC-011");
        detail.setReturnStatus("PENDING");
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-011");
        copy.setStatus("RESERVED");
        copy.setLockedByUser(readerId);

        when(ticketRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ticket));
        when(detailRepository.findByTicketIdForUpdate(11L)).thenReturn(List.of(detail));
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-011"))).thenReturn(List.of(copy));

        LoanStateResponseDTO result = service.rejectReservation(11L, librarianId, "Thẻ thư viện hết hạn");

        assertThat(result.getStatus()).isEqualTo(LoanTicketStatus.REJECTED);
        assertThat(detail.getReturnStatus()).isEqualTo("CANCELLED");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        assertThat(copy.getLockedByUser()).isNull();
        verify(detailRepository).save(detail);
        verify(copyRepository).save(copy);
        verify(waitlistService).promoteNext(eq(copy), any());
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

    @Test
    void manualCheckoutCreatesBorrowingTicketForEligibleReader() {
        UUID readerId = UUID.randomUUID();
        UUID librarianId = UUID.randomUUID();
        UserEntity reader = new UserEntity();
        reader.setUserId(readerId);
        reader.setMssv("2001234567");
        reader.setCardStatus("ACTIVE");
        BookCopyEntity first = availableCopy("BC-101", 101L);
        BookCopyEntity second = availableCopy("BC-102", 102L);
        ManualCheckoutRequestDTO request = new ManualCheckoutRequestDTO();
        request.setIdempotencyKey("manual-checkout-1");
        request.setMssv("2001234567");
        request.setTicketType("BORROW");
        request.setBarcodes(List.of("BC-102", "BC-101"));
        request.setNote("Sách nguyên vẹn");

        when(operationRepository.findByIdempotencyKey("manual-checkout-1")).thenReturn(Optional.empty());
        when(userRepository.findByMssv("2001234567")).thenReturn(Optional.of(reader));
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(reader));
        when(walletRepository.findByUserId(readerId)).thenReturn(Optional.empty());
        when(detailRepository.countOverdueItemsByUserId(eq(readerId), any())).thenReturn(0L);
        when(detailRepository.countOpenItemsByUserId(eq(readerId), any())).thenReturn(0L);
        when(detailRepository.countOpenItemsByUserIdAndBookId(eq(readerId), any())).thenReturn(0L);
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-101", "BC-102")))
                .thenReturn(List.of(first, second));
        when(ticketRepository.save(any(LoanTicketEntity.class))).thenAnswer(invocation -> {
            LoanTicketEntity value = invocation.getArgument(0);
            value.setTicketId(81L);
            return value;
        });
        when(operationRepository.saveAndFlush(any(CirculationOperationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.manualCheckout(request, librarianId);

        assertThat(result.getTicketIds()).containsExactly(81L);
        assertThat(result.getBarcodes()).containsExactly("BC-101", "BC-102");
        assertThat(result.getStatus()).isEqualTo("BORROWING");
        assertThat(first.getStatus()).isEqualTo("BORROWING");
        assertThat(second.getStatus()).isEqualTo("BORROWING");
        verify(detailRepository, times(2)).save(any(LoanDetailEntity.class));
        verify(historyRepository).save(any(LoanStatusHistoryEntity.class));
    }

    private BookCopyEntity availableCopy(String barcode, Long bookId) {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode(barcode);
        copy.setBookId(bookId);
        copy.setStatus("AVAILABLE");
        return copy;
    }

    private LoanTicketEntity ticket(Long id, String status) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(id);
        ticket.setStatus(status);
        return ticket;
    }
}
