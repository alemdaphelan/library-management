package com.huit.library.modules.penalty.service;

import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LateFineServiceTest {
    private LoanDetailRepository detailRepository;
    private WalletRepository walletRepository;
    private TransactionRepository transactionRepository;
    private LateFineService service;

    @BeforeEach
    void setUp() {
        detailRepository = mock(LoanDetailRepository.class);
        walletRepository = mock(WalletRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        service = new LateFineService(
                detailRepository, walletRepository, transactionRepository, new BigDecimal("1000"));
    }

    @Test
    void accruesByCalendarDayAndCreatesDebtTransactionOnce() {
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 10, 17, 12, 0);
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(10L, readerId);
        LoanDetailEntity detail = detail(20L, calculatedAt.minusHours(25));
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(readerId);
        wallet.setTotalDebt(new BigDecimal("500"));

        when(walletRepository.findByUserIdForUpdate(readerId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByDedupKey("LATE_FINE:20:2026-10-17")).thenReturn(false);

        BigDecimal added = service.accrueLateFine(ticket, detail, calculatedAt);

        assertThat(added).isEqualByComparingTo("1000");
        assertThat(detail.getLateFineAccrued()).isEqualByComparingTo("1000");
        assertThat(wallet.getTotalDebt()).isEqualByComparingTo("1500");

        ArgumentCaptor<TransactionEntity> transactionCaptor =
                ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo("1000");
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo("OUTSTANDING");
        assertThat(transactionCaptor.getValue().getUserId()).isEqualTo(readerId);

        assertThat(service.accrueLateFine(ticket, detail, calculatedAt))
                .isEqualByComparingTo(BigDecimal.ZERO);
        verify(walletRepository, times(1)).save(any());
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void doesNothingBeforeDueDate() {
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 10, 17, 12, 0);
        LoanTicketEntity ticket = ticket(11L, UUID.randomUUID());
        LoanDetailEntity detail = detail(21L, calculatedAt.plusMinutes(1));

        assertThat(service.accrueLateFine(ticket, detail, calculatedAt))
                .isEqualByComparingTo(BigDecimal.ZERO);

        verifyNoInteractions(walletRepository, transactionRepository);
        verify(detailRepository, never()).save(any());
    }

    @Test
    void usesSecurityDepositBeforeCreatingRemainingDebt() {
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 10, 17, 12, 0);
        UUID readerId = UUID.randomUUID();
        LoanTicketEntity ticket = ticket(12L, readerId);
        LoanDetailEntity detail = detail(22L, calculatedAt.minusDays(2));
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(readerId);
        wallet.setDepositBalance(new BigDecimal("1500"));
        wallet.setTotalDebt(BigDecimal.ZERO);
        when(walletRepository.findByUserIdForUpdate(readerId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByDedupKey("LATE_FINE:22:2026-10-17")).thenReturn(false);

        service.accrueLateFine(ticket, detail, calculatedAt);

        assertThat(wallet.getDepositBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getTotalDebt()).isEqualByComparingTo("500");
        verify(transactionRepository).save(argThat(transaction ->
                transaction.getPaidAmount().compareTo(new BigDecimal("1500")) == 0
                        && "PARTIALLY_PAID".equals(transaction.getStatus())
                        && "SECURITY_DEPOSIT".equals(transaction.getPaymentMethod())));
    }

    private LoanTicketEntity ticket(Long ticketId, UUID userId) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(ticketId);
        ticket.setUserId(userId);
        return ticket;
    }

    private LoanDetailEntity detail(Long detailId, LocalDateTime dueDate) {
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setDetailId(detailId);
        detail.setBarcode("BC-" + detailId);
        detail.setDueDate(dueDate);
        detail.setLateFineAccrued(BigDecimal.ZERO);
        return detail;
    }
}
