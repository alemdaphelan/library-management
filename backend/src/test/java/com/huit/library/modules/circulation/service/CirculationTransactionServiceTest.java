package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.CirculationOperationResponseDTO;
import com.huit.library.modules.circulation.dto.ReturnRequestDTO;
import com.huit.library.modules.circulation.entity.CirculationOperationEntity;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.CirculationOperationRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanStatusHistoryRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.penalty.service.LateFineService;
import com.huit.library.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CirculationTransactionServiceTest {
    private LoanTicketRepository ticketRepository;
    private LoanDetailRepository detailRepository;
    private LoanStatusHistoryRepository historyRepository;
    private BookCopyRepository copyRepository;
    private WalletRepository walletRepository;
    private UserRepository userRepository;
    private CirculationOperationRepository operationRepository;
    private LateFineService lateFineService;
    private CirculationStateService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(LoanTicketRepository.class);
        detailRepository = mock(LoanDetailRepository.class);
        historyRepository = mock(LoanStatusHistoryRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        walletRepository = mock(WalletRepository.class);
        userRepository = mock(UserRepository.class);
        operationRepository = mock(CirculationOperationRepository.class);
        lateFineService = mock(LateFineService.class);
        lenient().when(lateFineService.accrueLateFine(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        service = new CirculationStateService(
                ticketRepository,
                detailRepository,
                historyRepository,
                copyRepository,
                userRepository,
                walletRepository,
                operationRepository,
                new CirculationStateMachine(),
                lateFineService,
                mock(WaitlistService.class),
                mock(NotificationService.class));
    }

    @Test
    void checkoutUpdatesTicketDetailAndCopyWithTenDayDueDate() {
        UUID readerId = UUID.randomUUID();
        UUID librarianId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(10L, readerId, "RESERVED");
        LoanDetailEntity detail = detail(10L, "BC-010", "PENDING");
        BookCopyEntity copy = copy("BC-010", "RESERVED", readerId);
        CheckoutRequestDTO request = checkoutRequest("checkout-10", 10L, "BC-010");

        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(operationRepository.findByIdempotencyKey("checkout-10")).thenReturn(Optional.empty());
        when(detailRepository.findByTicketIdForUpdate(10L)).thenReturn(List.of(detail));
        when(walletRepository.findByUserId(readerId)).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(new UserEntity()));
        when(detailRepository.countOverdueItemsByUserId(eq(readerId), any())).thenReturn(0L);
        when(detailRepository.countOpenItemsByUserId(readerId, List.of("BORROWING", "OVERDUE"))).thenReturn(0L);
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-010"))).thenReturn(List.of(copy));
        saveOperationWithTimestamp();

        LocalDateTime before = LocalDateTime.now().plusDays(10).minusSeconds(1);
        CirculationOperationResponseDTO response = service.checkout(request, librarianId);
        LocalDateTime after = LocalDateTime.now().plusDays(10).plusSeconds(1);

        assertThat(response.getStatus()).isEqualTo("BORROWING");
        assertThat(ticket.getStatus()).isEqualTo("BORROWING");
        assertThat(detail.getReturnStatus()).isEqualTo("BORROWING");
        assertThat(detail.getDueDate()).isBetween(before, after);
        assertThat(copy.getStatus()).isEqualTo("BORROWING");
        assertThat(copy.getLockedByUser()).isNull();
        verify(historyRepository).save(any());
    }

    @Test
    void repeatedCheckoutWithSameIdempotencyKeyReplaysSavedResult() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(11L, readerId, "RESERVED");
        LoanDetailEntity detail = detail(11L, "BC-011", "PENDING");
        BookCopyEntity copy = copy("BC-011", "RESERVED", readerId);
        CheckoutRequestDTO request = checkoutRequest("checkout-11", 11L, "BC-011");
        AtomicReference<CirculationOperationEntity> saved = new AtomicReference<>();

        when(ticketRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ticket));
        when(operationRepository.findByIdempotencyKey("checkout-11"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(detailRepository.findByTicketIdForUpdate(11L)).thenReturn(List.of(detail));
        when(walletRepository.findByUserId(readerId)).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(new UserEntity()));
        when(detailRepository.countOverdueItemsByUserId(eq(readerId), any())).thenReturn(0L);
        when(detailRepository.countOpenItemsByUserId(readerId, List.of("BORROWING", "OVERDUE"))).thenReturn(0L);
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-011"))).thenReturn(List.of(copy));
        when(operationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CirculationOperationEntity operation = invocation.getArgument(0);
            operation.setProcessedAt(LocalDateTime.now());
            saved.set(operation);
            return operation;
        });

        CirculationOperationResponseDTO first = service.checkout(request, UUID.randomUUID());
        CirculationOperationResponseDTO replay = service.checkout(request, UUID.randomUUID());

        assertThat(first.isReplayed()).isFalse();
        assertThat(replay.isReplayed()).isTrue();
        assertThat(replay.getOperationId()).isEqualTo(first.getOperationId());
        verify(historyRepository, times(1)).save(any());
        verify(operationRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void returnUpdatesAllThreeLayersAndCompletesTicket() {
        UUID librarianId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(12L, UUID.randomUUID(), "BORROWING");
        LoanDetailEntity detail = detail(12L, "BC-012", "BORROWING");
        BookCopyEntity copy = copy("BC-012", "BORROWING", null);
        ReturnRequestDTO request = new ReturnRequestDTO();
        request.setIdempotencyKey("return-12");
        request.setBarcodes(List.of("BC-012"));
        request.setReturnConditionNote("Tình trạng tốt");

        when(operationRepository.findByIdempotencyKey("return-12")).thenReturn(Optional.empty());
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-012"))).thenReturn(List.of(copy));
        when(detailRepository.findActiveByBarcodesForUpdate(List.of("BC-012"))).thenReturn(List.of(detail));
        when(ticketRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(ticket));
        when(detailRepository.findByTicketIdForUpdate(12L)).thenReturn(List.of(detail));
        saveOperationWithTimestamp();

        CirculationOperationResponseDTO response = service.returnBooks(request, librarianId);

        assertThat(response.getStatus()).isEqualTo("RETURNED");
        assertThat(ticket.getStatus()).isEqualTo("RETURNED");
        assertThat(detail.getReturnStatus()).isEqualTo("RETURNED");
        assertThat(detail.getReturnDate()).isNotNull();
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        assertThat(copy.getConditionNote()).isEqualTo("Tình trạng tốt");
        verify(historyRepository).save(any());
    }

    @Test
    void oneReturnOperationCanCompleteItemsFromDifferentTickets() {
        UUID librarianId = UUID.randomUUID();
        LoanTicketEntity firstTicket = ticket(21L, UUID.randomUUID(), "BORROWING");
        LoanTicketEntity secondTicket = ticket(22L, UUID.randomUUID(), "BORROWING");
        LoanDetailEntity firstDetail = detail(21L, "BC-021", "BORROWING");
        LoanDetailEntity secondDetail = detail(22L, "BC-022", "BORROWING");
        BookCopyEntity firstCopy = copy("BC-021", "BORROWING", null);
        BookCopyEntity secondCopy = copy("BC-022", "BORROWING", null);
        ReturnRequestDTO request = new ReturnRequestDTO();
        request.setIdempotencyKey("return-multi-ticket");
        request.setBarcodes(List.of("BC-021", "BC-022"));

        when(operationRepository.findByIdempotencyKey("return-multi-ticket")).thenReturn(Optional.empty());
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-021", "BC-022")))
                .thenReturn(List.of(firstCopy, secondCopy));
        when(detailRepository.findActiveByBarcodesForUpdate(List.of("BC-021", "BC-022")))
                .thenReturn(List.of(firstDetail, secondDetail));
        when(ticketRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(firstTicket));
        when(ticketRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(secondTicket));
        when(detailRepository.findByTicketIdForUpdate(21L)).thenReturn(List.of(firstDetail));
        when(detailRepository.findByTicketIdForUpdate(22L)).thenReturn(List.of(secondDetail));
        saveOperationWithTimestamp();

        CirculationOperationResponseDTO response = service.returnBooks(request, librarianId);

        assertThat(response.getTicketIds()).containsExactly(21L, 22L);
        assertThat(response.getBarcodes()).containsExactly("BC-021", "BC-022");
        assertThat(firstTicket.getStatus()).isEqualTo("RETURNED");
        assertThat(secondTicket.getStatus()).isEqualTo("RETURNED");
        assertThat(firstCopy.getStatus()).isEqualTo("AVAILABLE");
        assertThat(secondCopy.getStatus()).isEqualTo("AVAILABLE");
        verify(historyRepository, times(2)).save(any());
    }

    @Test
    void reservationIsBlockedWhenReaderHasDebtOrThreeOpenItems() {
        UUID readerId = UUID.randomUUID();
        WalletEntity wallet = new WalletEntity();
        wallet.setTotalDebt(BigDecimal.valueOf(1000));
        when(walletRepository.findByUserId(readerId)).thenReturn(Optional.of(wallet));
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> service.ensureEligibleForReservation(readerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("còn nợ");

        wallet.setTotalDebt(BigDecimal.ZERO);
        when(detailRepository.countOverdueItemsByUserId(eq(readerId), any())).thenReturn(0L);
        when(detailRepository.countOpenItemsByUserId(
                readerId, List.of("PENDING", "RESERVED", "BORROWING", "OVERDUE"))).thenReturn(3L);

        assertThatThrownBy(() -> service.ensureEligibleForReservation(readerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tối đa 3");
    }

    private void saveOperationWithTimestamp() {
        when(operationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CirculationOperationEntity operation = invocation.getArgument(0);
            operation.setProcessedAt(LocalDateTime.now());
            return operation;
        });
    }

    private CheckoutRequestDTO checkoutRequest(String key, Long ticketId, String barcode) {
        CheckoutRequestDTO request = new CheckoutRequestDTO();
        request.setIdempotencyKey(key);
        request.setTicketId(ticketId);
        request.setBarcodes(List.of(barcode));
        return request;
    }

    private LoanTicketEntity ticket(Long id, UUID userId, String status) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(id);
        ticket.setUserId(userId);
        ticket.setTicketType("BORROW");
        ticket.setStatus(status);
        return ticket;
    }

    private LoanDetailEntity detail(Long ticketId, String barcode, String status) {
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(ticketId);
        detail.setBarcode(barcode);
        detail.setReturnStatus(status);
        return detail;
    }

    private BookCopyEntity copy(String barcode, String status, UUID lockedBy) {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode(barcode);
        copy.setStatus(status);
        copy.setLockedByUser(lockedBy);
        return copy;
    }
}
