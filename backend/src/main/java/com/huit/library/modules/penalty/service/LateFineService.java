package com.huit.library.modules.penalty.service;

import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class LateFineService {
    private final LoanDetailRepository loanDetailRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final BigDecimal homeDailyRate;
    private final BigDecimal onsiteDailyRate;

    @Autowired
    public LateFineService(
            LoanDetailRepository loanDetailRepository,
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            @Value("${library.circulation.late-fine-per-day:1000}") BigDecimal homeDailyRate,
            @Value("${library.circulation.onsite-late-fine-per-day:5000}") BigDecimal onsiteDailyRate) {
        this.loanDetailRepository = loanDetailRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.homeDailyRate = homeDailyRate;
        this.onsiteDailyRate = onsiteDailyRate;
    }

    public LateFineService(
            LoanDetailRepository loanDetailRepository,
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            BigDecimal homeDailyRate) {
        this(loanDetailRepository, walletRepository, transactionRepository,
                homeDailyRate, homeDailyRate.multiply(BigDecimal.valueOf(5)));
    }

    public BigDecimal accrueLateFine(
            LoanTicketEntity ticket,
            LoanDetailEntity detail,
            LocalDateTime calculatedAt) {
        LocalDateTime effectiveAt = detail.getFineStoppedAt() != null
                && detail.getFineStoppedAt().isBefore(calculatedAt)
                ? detail.getFineStoppedAt()
                : calculatedAt;
        if (detail.getDueDate() == null || !detail.getDueDate().isBefore(effectiveAt)) {
            return BigDecimal.ZERO;
        }

        long chargeableDays = ChronoUnit.DAYS.between(
                detail.getDueDate().toLocalDate(), effectiveAt.toLocalDate());
        if (chargeableDays <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = "ONSITE".equalsIgnoreCase(ticket.getTicketType())
                ? onsiteDailyRate : homeDailyRate;
        BigDecimal expectedFine = rate
                .multiply(BigDecimal.valueOf(chargeableDays))
                .setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal currentFine = detail.getLateFineAccrued() == null
                ? BigDecimal.ZERO
                : detail.getLateFineAccrued();
        BigDecimal additionalFine = expectedFine.subtract(currentFine);
        if (additionalFine.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        detail.setLateFineAccrued(expectedFine);
        loanDetailRepository.save(detail);

        WalletEntity wallet = walletRepository.findByUserIdForUpdate(ticket.getUserId())
                .orElseGet(() -> newWallet(ticket));
        BigDecimal existingDebt = wallet.getTotalDebt() == null ? BigDecimal.ZERO : wallet.getTotalDebt();
        wallet.setTotalDebt(existingDebt.add(additionalFine));
        walletRepository.save(wallet);

        String dedupKey = "LATE_FINE:" + detail.getDetailId() + ":" + effectiveAt.toLocalDate();
        if (!transactionRepository.existsByDedupKey(dedupKey)) {
            TransactionEntity transaction = new TransactionEntity();
            transaction.setUserId(ticket.getUserId());
            transaction.setTicketId(ticket.getTicketId());
            transaction.setLoanDetailId(detail.getDetailId());
            transaction.setAmount(additionalFine);
            transaction.setType("FINE");
            transaction.setReason("Phạt trả trễ " + chargeableDays + " ngày cho bản sao " + detail.getBarcode());
            transaction.setStatus("OUTSTANDING");
            transaction.setDedupKey(dedupKey);
            transactionRepository.save(transaction);
        }

        return additionalFine;
    }

    private WalletEntity newWallet(LoanTicketEntity ticket) {
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(ticket.getUserId());
        wallet.setDepositBalance(BigDecimal.ZERO);
        wallet.setTotalDebt(BigDecimal.ZERO);
        return wallet;
    }
}
