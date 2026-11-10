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
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.huit.library.modules.finance.entity.WalletEntity;

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
    private NotificationService notificationService;
    private LibraryPolicyService policyService;
    private BorrowingValuePolicyService borrowingValuePolicyService;
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
        notificationService = mock(NotificationService.class);
        policyService = mock(LibraryPolicyService.class);
        borrowingValuePolicyService = mock(BorrowingValuePolicyService.class);
        when(policyService.maximumOpenItems()).thenReturn(3);
        when(policyService.homeLoanDays()).thenReturn(10);
        when(policyService.recallReturnDays()).thenReturn(7);
        when(borrowingValuePolicyService.getSnapshot(any(), any())).thenReturn(
                new BorrowingValuePolicyService.BorrowingValueSnapshot(
                        new BigDecimal("100000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("150000"),
                        new BigDecimal("150000"),
                        true,
                        true,
                        true,
                        "Hạn mức giá trị còn lại là 150.000 đồng."));
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
                notificationService,
                mock(LibraryHoursService.class),
                policyService,
                borrowingValuePolicyService);
    }

    @Test
    void recallShortensDueDateToSevenDaysAndNotifiesReader() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(90L, "BORROWING");
        ticket.setUserId(readerId);
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setDetailId(901L);
        detail.setTicketId(90L);
        detail.setBarcode("BC-RECALL-901");
        detail.setReturnStatus("BORROWING");
        LocalDateTime originalDueDate = LocalDateTime.now().plusDays(20);
        detail.setDueDate(originalDueDate);

        when(detailRepository.findById(901L)).thenReturn(Optional.of(detail));
        when(detailRepository.findByIdForUpdate(901L)).thenReturn(Optional.of(detail));
        when(ticketRepository.findByIdForUpdate(90L)).thenReturn(Optional.of(ticket));

        var result = service.recallBook(901L, UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo("BORROWING");
        assertThat(detail.getIsRecalled()).isTrue();
        assertThat(detail.getOriginalDueDateBeforeRecall()).isEqualTo(originalDueDate);
        assertThat(detail.getRecallDueAt()).isEqualTo(detail.getDueDate());
        assertThat(detail.getRecallDueAt()).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(detail.getRecallDueAt()).isBefore(LocalDateTime.now().plusDays(8));
        verify(detailRepository).save(detail);
        verify(notificationService).createOnce(
                eq(readerId), eq("LOAN_RECALL"), contains("BC-RECALL-901"),
                eq("LOAN_DETAIL"), eq("901"), eq("LOAN_RECALL:901"), any(LocalDateTime.class));
    }

    @Test
    void legacyPendingReservationIsRevalidatedAndAutomaticallyAudited() {
        UUID librarianId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(10L, "PENDING");
        ticket.setUserId(readerId);
        UserEntity reader = new UserEntity();
        reader.setUserId(readerId);
        reader.setCardStatus("ACTIVE");
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(reader));
        when(detailRepository.countOpenItemsByUserId(eq(readerId), any())).thenReturn(1L);
        stubHeldDetail(ticket, LocalDateTime.now().plusHours(2));

        LoanStateResponseDTO result = service.approveReservation(10L, librarianId);

        assertThat(result.getPreviousStatus()).isEqualTo(LoanTicketStatus.PENDING);
        assertThat(result.getStatus()).isEqualTo(LoanTicketStatus.RESERVED);
        assertThat(ticket.getStatus()).isEqualTo("RESERVED");
        assertThat(ticket.getProcessedBy()).isNull();

        ArgumentCaptor<LoanStatusHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(LoanStatusHistoryEntity.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getTicketId()).isEqualTo(10L);
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(LoanTicketStatus.PENDING);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(LoanTicketStatus.RESERVED);
        assertThat(historyCaptor.getValue().getChangedBy()).isNull();
        assertThat(historyCaptor.getValue().getReason()).contains("Hệ thống tự động");
    }

    @Test
    void newlyCreatedReservationUsesStateMachineAndSystemActor() {
        LoanTicketEntity ticket = ticket(14L, "PENDING");
        var result = service.autoApproveReservation(ticket);
        assertThat(result.getStatus()).isEqualTo(LoanTicketStatus.RESERVED);
        assertThat(ticket.getProcessedBy()).isNull();
        ArgumentCaptor<LoanStatusHistoryEntity> audit = ArgumentCaptor.forClass(LoanStatusHistoryEntity.class);
        verify(historyRepository).save(audit.capture());
        assertThat(audit.getValue().getFromStatus()).isEqualTo(LoanTicketStatus.PENDING);
        assertThat(audit.getValue().getToStatus()).isEqualTo(LoanTicketStatus.RESERVED);
        assertThat(audit.getValue().getChangedBy()).isNull();
        assertThat(audit.getValue().getReason()).contains("Hệ thống tự động");
    }

    @Test
    void legacyValidationRejectsExpiredHoldWithoutChangingState() {
        LoanTicketEntity ticket = legacyTicket();
        stubHeldDetail(ticket, LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.approveReservation(ticket.getTicketId(), UUID.randomUUID()))
                .hasMessageContaining("hết hạn");
        assertThat(ticket.getStatus()).isEqualTo("PENDING");
        verify(historyRepository, never()).save(any());
    }

    @Test
    void legacyValidationRejectsACopyHeldForAnotherReader() {
        LoanTicketEntity ticket = legacyTicket();
        BookCopyEntity copy = stubHeldDetail(ticket, LocalDateTime.now().plusHours(2));
        copy.setLockedByUser(UUID.randomUUID());
        assertThatThrownBy(() -> service.approveReservation(ticket.getTicketId(), UUID.randomUUID()))
                .hasMessageContaining("bản sao");
        verify(historyRepository, never()).save(any());
    }

    @Test
    void legacyValidationRejectsEmptyTicket() {
        LoanTicketEntity ticket = legacyTicket();
        assertThatThrownBy(() -> service.approveReservation(ticket.getTicketId(), UUID.randomUUID()))
                .hasMessageContaining("đủ tài liệu");
        verify(historyRepository, never()).save(any());
    }

    @Test
    void compatibilityApprovalOfReservedTicketDoesNotWriteAnotherHistoryRow() {
        LoanTicketEntity ticket = legacyTicket();
        ticket.setStatus("RESERVED");
        stubHeldDetail(ticket, LocalDateTime.now().plusHours(2));
        assertThat(service.approveReservation(ticket.getTicketId(), UUID.randomUUID()).getStatus())
                .isEqualTo(LoanTicketStatus.RESERVED);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void legacyValidationRejectsDebtAndLockedOrExpiredCards() {
        LoanTicketEntity ticket = legacyTicket();
        UserEntity reader = userRepository.findByIdForUpdate(ticket.getUserId()).orElseThrow();
        WalletEntity wallet = new WalletEntity();
        wallet.setTotalDebt(new BigDecimal("150000"));
        when(walletRepository.findByUserId(ticket.getUserId())).thenReturn(Optional.of(wallet));
        assertThatThrownBy(() -> service.approveReservation(ticket.getTicketId(), UUID.randomUUID()))
                .hasMessageContaining("còn nợ");
        wallet.setTotalDebt(BigDecimal.ZERO);
        reader.setCardStatus("LOCKED");
        assertThatThrownBy(() -> service.approveReservation(ticket.getTicketId(), UUID.randomUUID()))
                .hasMessageContaining("bị khóa");
        reader.setCardStatus("ACTIVE");
        reader.setCardExpiryDate(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.approveReservation(ticket.getTicketId(), UUID.randomUUID()))
                .hasMessageContaining("hết hạn");
        reader.setCardExpiryDate(LocalDateTime.now().plusDays(1));
        when(detailRepository.countOverdueItemsByUserId(eq(ticket.getUserId()), any())).thenReturn(1L);
        assertThatThrownBy(() -> service.approveReservation(ticket.getTicketId(), UUID.randomUUID()))
                .hasMessageContaining("quá hạn");
        verify(historyRepository, never()).save(any());
    }

    private LoanTicketEntity legacyTicket() {
        LoanTicketEntity ticket = ticket(15L, "PENDING");
        ticket.setUserId(UUID.randomUUID());
        UserEntity reader = new UserEntity();
        reader.setUserId(ticket.getUserId());
        reader.setCardStatus("ACTIVE");
        when(ticketRepository.findByIdForUpdate(ticket.getTicketId())).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdForUpdate(ticket.getUserId())).thenReturn(Optional.of(reader));
        when(detailRepository.countOpenItemsByUserId(eq(ticket.getUserId()), any())).thenReturn(1L);
        return ticket;
    }

    private BookCopyEntity stubHeldDetail(LoanTicketEntity ticket, LocalDateTime deadline) {
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(ticket.getTicketId());
        detail.setBarcode("LEGACY-COPY");
        detail.setReturnStatus("PENDING");
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("LEGACY-COPY");
        copy.setStatus("RESERVED");
        copy.setLockedByUser(ticket.getUserId());
        copy.setLockedUntil(deadline);
        when(detailRepository.findByTicketIdForUpdate(ticket.getTicketId())).thenReturn(List.of(detail));
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("LEGACY-COPY"))).thenReturn(List.of(copy));
        return copy;
    }

    @Test
    void eligibilityCountsAllOpenItemsAndExplainsWhenLimitIsReached() {
        UUID readerId = UUID.randomUUID();
        UserEntity reader = new UserEntity();
        reader.setCardStatus("ACTIVE");
        when(userRepository.findById(readerId)).thenReturn(Optional.of(reader));
        when(detailRepository.countOpenItemsByUserId(eq(readerId), any())).thenReturn(3L);

        var result = service.getBorrowEligibility(readerId);

        assertThat(result.getOpenItems()).isEqualTo(3);
        assertThat(result.getRemainingSlots()).isZero();
        assertThat(result.isEligible()).isFalse();
        assertThat(result.getMessage()).contains("đủ 3 tài liệu");
    }

    @Test
    void blocksReservationAndEligibilityUntilTheCardIsIssued() {
        UUID readerId = UUID.randomUUID();
        UserEntity pending = new UserEntity();
        when(userRepository.findById(readerId)).thenReturn(Optional.of(pending));
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(pending));

        var eligibility = service.getBorrowEligibility(readerId);

        assertThat(eligibility.isCardEligible()).isFalse();
        assertThat(eligibility.isEligible()).isFalse();
        assertThat(eligibility.getMessage()).contains("lớp hướng dẫn");
        assertThatThrownBy(() -> service.ensureEligibleForReservation(readerId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("lớp hướng dẫn");
        assertThatThrownBy(() -> service.ensureEligibleForRenewal(readerId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("lớp hướng dẫn");
    }

    @Test
    void approvalIsRejectedWhenReaderAlreadyHasMoreThanThreeOpenItems() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(13L, "PENDING");
        ticket.setUserId(readerId);
        UserEntity reader = new UserEntity();
        reader.setUserId(readerId);
        reader.setCardStatus("ACTIVE");
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

    @Test
    void cancelReservationReleasesReservedCopyAndPromotesWaitlist() {
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(55L, "RESERVED");
        ticket.setUserId(readerId);
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(55L);
        detail.setBarcode("BC-055");
        detail.setReturnStatus("PENDING");
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-055");
        copy.setBookId(55L);
        copy.setStatus("RESERVED");
        copy.setLockedByUser(readerId);

        when(detailRepository.findByTicketIdForUpdate(55L)).thenReturn(List.of(detail));
        when(copyRepository.findAllByBarcodeInForUpdate(List.of("BC-055"))).thenReturn(List.of(copy));

        LoanStateResponseDTO result = service.cancelReservation(ticket, readerId);

        assertThat(result.getStatus()).isEqualTo(LoanTicketStatus.CANCELLED);
        assertThat(ticket.getStatus()).isEqualTo("CANCELLED");
        assertThat(detail.getReturnStatus()).isEqualTo("CANCELLED");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        assertThat(copy.getLockedByUser()).isNull();
        verify(waitlistService).promoteNext(eq(copy), any());
    }

    @Test
    void returningOrCancellingItemFreesQuotaForNextReservation() {
        UUID readerId = UUID.randomUUID();
        UserEntity reader = new UserEntity();
        reader.setUserId(readerId);
        reader.setCardStatus("ACTIVE");
        when(userRepository.findByIdForUpdate(readerId)).thenReturn(Optional.of(reader));
        when(userRepository.findById(readerId)).thenReturn(Optional.of(reader));
        when(walletRepository.findByUserId(readerId)).thenReturn(Optional.empty());
        when(detailRepository.countOverdueItemsByUserId(eq(readerId), any())).thenReturn(0L);
        when(detailRepository.countOpenItemsByUserId(
                readerId, List.of("PENDING", "RESERVED", "BORROWING", "OVERDUE")))
                .thenReturn(3L, 2L);

        assertThatThrownBy(() -> service.ensureEligibleForReservation(readerId, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tối đa 3 tài liệu");

        service.ensureEligibleForReservation(readerId, 1);
        var eligibility = service.getBorrowEligibility(readerId);
        assertThat(eligibility.isEligible()).isTrue();
        assertThat(eligibility.getRemainingSlots()).isEqualTo(1);
    }

    private void unusedEndHelper() {
        assertThat(historyRepository).isNotNull();
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
