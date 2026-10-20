package com.huit.library.modules.penalty.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.penalty.dto.PenaltyCaseRequestDTO;
import com.huit.library.modules.penalty.dto.ReplacementAcceptanceRequestDTO;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.repository.CompensationRateRepository;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PenaltyCaseServiceTest {
    private PenaltyCaseRepository cases;
    private LoanDetailRepository details;
    private LoanTicketRepository tickets;
    private BookCopyRepository copies;
    private TransactionRepository transactions;
    private WalletRepository wallets;
    private PenaltyCaseService service;

    @BeforeEach
    void setUp() {
        cases = mock(PenaltyCaseRepository.class);
        details = mock(LoanDetailRepository.class);
        tickets = mock(LoanTicketRepository.class);
        copies = mock(BookCopyRepository.class);
        transactions = mock(TransactionRepository.class);
        wallets = mock(WalletRepository.class);
        AtomicLong ids = new AtomicLong(1);
        when(cases.save(any())).thenAnswer(invocation -> {
            PenaltyCaseEntity value = invocation.getArgument(0);
            if (value.getCaseId() == null) value.setCaseId(ids.getAndIncrement());
            return value;
        });
        service = new PenaltyCaseService(cases, mock(CompensationRateRepository.class), details,
                tickets, copies, transactions, wallets, mock(LateFineService.class),
                mock(NotificationService.class), new BigDecimal("50000"));
    }

    @Test
    void oldVietnameseBookWithCoverPriceUsesFiveTimesPricePlusTechnicalFee() {
        UUID userId = UUID.randomUUID();
        LoanDetailEntity detail = detail(10L, 20L, "BC-LOST");
        LoanTicketEntity ticket = ticket(20L, userId);
        BookCopyEntity copy = copy("BC-LOST", 30L);
        WalletEntity wallet = wallet(userId);
        when(details.findByIdForUpdate(10L)).thenReturn(Optional.of(detail));
        when(cases.existsByLoanDetailIdAndStatusNotIn(eq(10L), anyList())).thenReturn(false);
        when(tickets.findById(20L)).thenReturn(Optional.of(ticket));
        when(copies.findById("BC-LOST")).thenReturn(Optional.of(copy));
        when(wallets.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        PenaltyCaseRequestDTO request = baseRequest(10L, "CASH");
        request.setCoverPrice(new BigDecimal("100000"));
        PenaltyCaseEntity result = service.createCase(request, UUID.randomUUID());

        assertThat(result.getCompensationAmount()).isEqualByComparingTo("500000");
        assertThat(result.getTechnicalFee()).isEqualByComparingTo("50000");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("550000");
        assertThat(detail.getFineStoppedAt()).isNotNull();
        assertThat(detail.getReturnStatus()).isEqualTo("LOST");
        assertThat(wallet.getTotalDebt()).isEqualByComparingTo("550000");
        verify(transactions, times(2)).save(any());
    }

    @Test
    void acceptedReplacementCreatesNewCopyAndClosesFullyPaidCase() {
        UUID userId = UUID.randomUUID();
        LoanDetailEntity detail = detail(11L, 21L, "BC-OLD");
        LoanTicketEntity ticket = ticket(21L, userId);
        PenaltyCaseEntity penaltyCase = new PenaltyCaseEntity();
        penaltyCase.setCaseId(2L);
        penaltyCase.setUserId(userId);
        penaltyCase.setTicketId(21L);
        penaltyCase.setLoanDetailId(11L);
        penaltyCase.setBarcode("BC-OLD");
        penaltyCase.setSettlementMethod("REPLACEMENT");
        penaltyCase.setTotalAmount(new BigDecimal("50000"));
        penaltyCase.setPaidAmount(new BigDecimal("50000"));
        when(cases.findByIdForUpdate(2L)).thenReturn(Optional.of(penaltyCase));
        when(copies.existsById("BC-NEW")).thenReturn(false);
        when(copies.findById("BC-OLD")).thenReturn(Optional.of(copy("BC-OLD", 31L)));
        when(details.findById(11L)).thenReturn(Optional.of(detail));
        when(tickets.findById(21L)).thenReturn(Optional.of(ticket));
        when(details.findByTicketId(21L)).thenReturn(List.of(detail));
        ReplacementAcceptanceRequestDTO request = new ReplacementAcceptanceRequestDTO();
        request.setReplacementBarcode("BC-NEW");
        request.setVerificationNote("Đúng nhan đề, tác giả và năm xuất bản mới hơn");

        PenaltyCaseEntity result = service.acceptReplacement(2L, request, UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getReplacementBarcode()).isEqualTo("BC-NEW");
        assertThat(detail.getReturnStatus()).isEqualTo("SETTLED");
        assertThat(ticket.getStatus()).isEqualTo("RETURNED");
        verify(copies).save(argThat(value -> "BC-NEW".equals(value.getBarcode())
                && "AVAILABLE".equals(value.getStatus()) && value.getBookId().equals(31L)));
    }

    private PenaltyCaseRequestDTO baseRequest(Long detailId, String settlement) {
        PenaltyCaseRequestDTO request = new PenaltyCaseRequestDTO();
        request.setLoanDetailId(detailId);
        request.setIncidentType("LOST");
        request.setDescription("Bạn đọc báo mất tài liệu");
        request.setMaterialType("BOOK");
        request.setLanguageCategory("VIETNAMESE");
        request.setStillInMarket(false);
        request.setSettlementMethod(settlement);
        return request;
    }

    private LoanDetailEntity detail(Long id, Long ticketId, String barcode) {
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setDetailId(id);
        detail.setTicketId(ticketId);
        detail.setBarcode(barcode);
        detail.setReturnStatus("BORROWING");
        detail.setLateFineAccrued(BigDecimal.ZERO);
        return detail;
    }

    private LoanTicketEntity ticket(Long id, UUID userId) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(id);
        ticket.setUserId(userId);
        ticket.setStatus("BORROWING");
        return ticket;
    }

    private BookCopyEntity copy(String barcode, Long bookId) {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode(barcode);
        copy.setBookId(bookId);
        copy.setStatus("BORROWING");
        return copy;
    }

    private WalletEntity wallet(UUID userId) {
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        wallet.setTotalDebt(BigDecimal.ZERO);
        wallet.setDepositBalance(BigDecimal.ZERO);
        return wallet;
    }
}
