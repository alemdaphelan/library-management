package com.huit.library.modules.finance.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.dto.AdjustmentReviewRequestDTO;
import com.huit.library.modules.finance.dto.DebtPaymentRequestDTO;
import com.huit.library.modules.finance.dto.PaymentReceiptDTO;
import com.huit.library.modules.finance.entity.TransactionAdjustmentEntity;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.TransactionAdjustmentRepository;
import com.huit.library.modules.finance.repository.PaymentAllocationRepository;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
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

class DebtLedgerServiceTest {
    private TransactionRepository transactions;
    private TransactionAdjustmentRepository adjustments;
    private WalletRepository wallets;
    private DebtLedgerService service;

    @BeforeEach
    void setUp() {
        transactions = mock(TransactionRepository.class);
        adjustments = mock(TransactionAdjustmentRepository.class);
        wallets = mock(WalletRepository.class);
        AtomicLong ids = new AtomicLong(100);
        when(transactions.save(any())).thenAnswer(invocation -> {
            TransactionEntity value = invocation.getArgument(0);
            if (value.getTransactionId() == null) value.setTransactionId(ids.getAndIncrement());
            return value;
        });
        when(adjustments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new DebtLedgerService(transactions, adjustments, mock(PaymentAllocationRepository.class), wallets,
                mock(PenaltyCaseRepository.class), mock(LoanDetailRepository.class),
                mock(LoanTicketRepository.class), mock(UserRepository.class),
                mock(NotificationService.class));
    }

    @Test
    void partialPaymentAllocatesDebtAndKeepsRemainingBalanceLocked() {
        UUID userId = UUID.randomUUID();
        UUID librarianId = UUID.randomUUID();
        WalletEntity wallet = wallet(userId, "1000");
        TransactionEntity debt = debt(userId, "1000", "0");
        DebtPaymentRequestDTO request = new DebtPaymentRequestDTO();
        request.setUserId(userId);
        request.setAmount(new BigDecimal("300"));
        request.setMethod("cash");
        request.setIdempotencyKey("receipt-1");
        when(transactions.findByDedupKey("PAYMENT:receipt-1")).thenReturn(Optional.empty());
        when(wallets.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(transactions.findPayableByUserIdForUpdate(userId)).thenReturn(List.of(debt));

        PaymentReceiptDTO payment = service.recordPayment(request, librarianId);

        assertThat(wallet.getTotalDebt()).isEqualByComparingTo("700");
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("300");
        assertThat(debt.getStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("300");
        assertThat(payment.getPaymentMethod()).isEqualTo("CASH");
        assertThat(payment.getReceiptNumber()).startsWith("RCPT-");
        assertThat(payment.getAllocations()).singleElement().satisfies(line -> {
            assertThat(line.getFeeType()).isEqualTo("FINE");
            assertThat(line.getAmount()).isEqualByComparingTo("300");
        });
    }

    @Test
    void approvedCorrectionCreatesReverseEntryInsteadOfDeletingOriginal() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        WalletEntity wallet = wallet(userId, "900");
        TransactionEntity original = debt(userId, "1000", "100");
        original.setTransactionId(20L);
        TransactionAdjustmentEntity adjustment = new TransactionAdjustmentEntity();
        adjustment.setAdjustmentId(30L);
        adjustment.setTransactionId(20L);
        adjustment.setRequestedAmount(new BigDecimal("400"));
        adjustment.setReason("Nhập nhầm số tiền");
        adjustment.setStatus("PENDING");
        AdjustmentReviewRequestDTO request = new AdjustmentReviewRequestDTO();
        request.setAction("APPROVE");
        when(adjustments.findById(30L)).thenReturn(Optional.of(adjustment));
        when(transactions.findByIdForUpdate(20L)).thenReturn(Optional.of(original));
        when(wallets.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        TransactionAdjustmentEntity result = service.reviewAdjustment(30L, request, adminId);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(original.getPaidAmount()).isEqualByComparingTo("500");
        assertThat(original.getStatus()).isEqualTo("PARTIALLY_ADJUSTED");
        assertThat(wallet.getTotalDebt()).isEqualByComparingTo("500");
        verify(transactions).save(argThat(value -> "ADJUSTMENT".equals(value.getType())
                && value.getAmount().compareTo(new BigDecimal("-400")) == 0
                && value.getParentTransactionId().equals(20L)));
        verify(transactions, never()).delete(any());
    }

    private WalletEntity wallet(UUID userId, String debt) {
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        wallet.setTotalDebt(new BigDecimal(debt));
        wallet.setDepositBalance(BigDecimal.ZERO);
        return wallet;
    }

    private TransactionEntity debt(UUID userId, String amount, String paid) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(userId);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setPaidAmount(new BigDecimal(paid));
        transaction.setStatus("OUTSTANDING");
        transaction.setType("FINE");
        return transaction;
    }
}
