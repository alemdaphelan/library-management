package com.huit.library.modules.finance.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.dto.AdjustmentRequestDTO;
import com.huit.library.modules.finance.dto.AdjustmentReviewRequestDTO;
import com.huit.library.modules.finance.dto.DebtPaymentRequestDTO;
import com.huit.library.modules.finance.dto.PaymentReceiptDTO;
import com.huit.library.modules.finance.entity.PaymentAllocationEntity;
import com.huit.library.modules.finance.entity.TransactionAdjustmentEntity;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.TransactionAdjustmentRepository;
import com.huit.library.modules.finance.repository.PaymentAllocationRepository;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Service
public class DebtLedgerService {
    private final TransactionRepository transactionRepository;
    private final TransactionAdjustmentRepository adjustmentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final WalletRepository walletRepository;
    private final PenaltyCaseRepository penaltyCaseRepository;
    private final LoanDetailRepository detailRepository;
    private final LoanTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DebtLedgerService(
            TransactionRepository transactionRepository,
            TransactionAdjustmentRepository adjustmentRepository,
            PaymentAllocationRepository allocationRepository,
            WalletRepository walletRepository,
            PenaltyCaseRepository penaltyCaseRepository,
            LoanDetailRepository detailRepository,
            LoanTicketRepository ticketRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.allocationRepository = allocationRepository;
        this.walletRepository = walletRepository;
        this.penaltyCaseRepository = penaltyCaseRepository;
        this.detailRepository = detailRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public PaymentReceiptDTO recordPayment(DebtPaymentRequestDTO request, UUID operatorId) {
        String method = request.getMethod().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CASH", "BANK_TRANSFER").contains(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ tiền mặt hoặc chuyển khoản");
        }
        String dedupKey = "PAYMENT:" + request.getIdempotencyKey().trim();
        TransactionEntity replay = transactionRepository.findByDedupKey(dedupKey).orElse(null);
        if (replay != null) {
            return buildReceipt(replay, request.getUserId());
        }

        WalletEntity wallet = walletRepository.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đọc chưa có công nợ"));
        BigDecimal debt = zero(wallet.getTotalDebt());
        if (request.getAmount().compareTo(debt) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền thu vượt quá công nợ hiện tại");
        }

        BigDecimal remaining = request.getAmount();
        List<PaymentReceiptDTO.AllocationLine> allocatedLines = new ArrayList<>();
        List<TransactionEntity> payable = transactionRepository.findPayableByUserIdForUpdate(request.getUserId());
        for (TransactionEntity debtEntry : payable) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal outstanding = debtEntry.getAmount().subtract(zero(debtEntry.getPaidAmount()));
            BigDecimal applied = outstanding.min(remaining);
            debtEntry.setPaidAmount(zero(debtEntry.getPaidAmount()).add(applied));
            debtEntry.setStatus(debtEntry.getPaidAmount().compareTo(debtEntry.getAmount()) >= 0
                    ? "PAID" : "PARTIALLY_PAID");
            debtEntry.setPaidAt(LocalDateTime.now());
            debtEntry.setConfirmedBy(operatorId);
            debtEntry.setPaymentMethod(method);
            transactionRepository.save(debtEntry);
            allocatedLines.add(PaymentReceiptDTO.AllocationLine.builder()
                    .debtTransactionId(debtEntry.getTransactionId())
                    .feeType(debtEntry.getType())
                    .reason(debtEntry.getReason())
                    .amount(applied)
                    .build());
            remaining = remaining.subtract(applied);
            if (debtEntry.getPenaltyCaseId() != null) {
                refreshPenaltyCase(debtEntry.getPenaltyCaseId());
            }
        }
        if (remaining.signum() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy đủ khoản nợ đến hạn để phân bổ");
        }

        wallet.setTotalDebt(debt.subtract(request.getAmount()));
        walletRepository.save(wallet);

        TransactionEntity payment = new TransactionEntity();
        payment.setUserId(request.getUserId());
        payment.setAmount(request.getAmount().negate());
        payment.setType("PAYMENT");
        payment.setReason(request.getNote() == null || request.getNote().isBlank()
                ? "Thu công nợ thư viện" : request.getNote().trim());
        payment.setStatus("COMPLETED");
        payment.setPaymentMethod(method);
        payment.setReceiptNumber("RCPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        payment.setConfirmedBy(operatorId);
        payment.setPaidAt(LocalDateTime.now());
        payment.setDedupKey(dedupKey);
        payment = transactionRepository.save(payment);
        for (PaymentReceiptDTO.AllocationLine line : allocatedLines) {
            PaymentAllocationEntity allocation = new PaymentAllocationEntity();
            allocation.setPaymentTransactionId(payment.getTransactionId());
            allocation.setDebtTransactionId(line.getDebtTransactionId());
            allocation.setAmount(line.getAmount());
            allocationRepository.save(allocation);
        }

        if (wallet.getTotalDebt().signum() == 0) {
            tryRestoreBorrowingRights(request.getUserId(), LocalDateTime.now());
        }
        notificationService.createOnce(
                request.getUserId(), "DEBT_PAYMENT",
                "Đã ghi nhận thanh toán " + request.getAmount().toPlainString()
                        + " đồng. Công nợ còn lại: " + wallet.getTotalDebt().toPlainString() + " đồng.",
                "TRANSACTION", payment.getTransactionId().toString(),
                "DEBT_PAYMENT:" + payment.getTransactionId(), LocalDateTime.now());
        return PaymentReceiptDTO.builder()
                .paymentTransactionId(payment.getTransactionId())
                .receiptNumber(payment.getReceiptNumber())
                .userId(payment.getUserId())
                .totalAmount(payment.getAmount().abs())
                .paymentMethod(payment.getPaymentMethod())
                .confirmedBy(payment.getConfirmedBy())
                .paidAt(payment.getPaidAt())
                .remainingDebt(wallet.getTotalDebt())
                .allocations(allocatedLines)
                .build();
    }

    @Transactional
    public TransactionAdjustmentEntity requestAdjustment(
            AdjustmentRequestDTO request, UUID librarianId) {
        TransactionEntity transaction = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể điều chỉnh giao dịch ghi nợ");
        }
        if (request.getAmount().compareTo(transaction.getAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền điều chỉnh vượt quá giao dịch gốc");
        }
        if (adjustmentRepository.existsByTransactionIdAndStatus(request.getTransactionId(), "PENDING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch đã có yêu cầu điều chỉnh đang chờ duyệt");
        }
        TransactionAdjustmentEntity adjustment = new TransactionAdjustmentEntity();
        adjustment.setTransactionId(request.getTransactionId());
        adjustment.setRequestedAmount(request.getAmount());
        adjustment.setReason(request.getReason().trim());
        adjustment.setStatus("PENDING");
        adjustment.setRequestedBy(librarianId);
        return adjustmentRepository.save(adjustment);
    }

    @Transactional
    public TransactionAdjustmentEntity reviewAdjustment(
            Long adjustmentId, AdjustmentReviewRequestDTO request, UUID adminId) {
        TransactionAdjustmentEntity adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu điều chỉnh"));
        if (!"PENDING".equals(adjustment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu điều chỉnh đã được xử lý");
        }
        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        adjustment.setReviewedBy(adminId);
        adjustment.setReviewNote(request.getNote());
        adjustment.setReviewedAt(LocalDateTime.now());
        if ("REJECT".equals(action)) {
            adjustment.setStatus("REJECTED");
            return adjustmentRepository.save(adjustment);
        }
        if (!"APPROVE".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action phải là APPROVE hoặc REJECT");
        }

        TransactionEntity original = transactionRepository.findByIdForUpdate(adjustment.getTransactionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch gốc"));
        WalletEntity wallet = walletRepository.findByUserIdForUpdate(original.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy công nợ người dùng"));
        BigDecimal reversible = original.getAmount().subtract(zero(original.getPaidAmount()));
        if (adjustment.getRequestedAmount().compareTo(reversible) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phần công nợ còn lại không đủ để điều chỉnh");
        }

        TransactionEntity reversal = new TransactionEntity();
        reversal.setParentTransactionId(original.getTransactionId());
        reversal.setUserId(original.getUserId());
        reversal.setTicketId(original.getTicketId());
        reversal.setLoanDetailId(original.getLoanDetailId());
        reversal.setPenaltyCaseId(original.getPenaltyCaseId());
        reversal.setAmount(adjustment.getRequestedAmount().negate());
        reversal.setType("ADJUSTMENT");
        reversal.setReason(adjustment.getReason());
        reversal.setStatus("COMPLETED");
        reversal.setConfirmedBy(adminId);
        reversal.setDedupKey("ADJUSTMENT:" + adjustmentId);
        reversal = transactionRepository.save(reversal);

        original.setPaidAmount(zero(original.getPaidAmount()).add(adjustment.getRequestedAmount()));
        original.setStatus(original.getPaidAmount().compareTo(original.getAmount()) >= 0
                ? "ADJUSTED" : "PARTIALLY_ADJUSTED");
        transactionRepository.save(original);
        wallet.setTotalDebt(zero(wallet.getTotalDebt()).subtract(adjustment.getRequestedAmount()).max(BigDecimal.ZERO));
        walletRepository.save(wallet);

        adjustment.setStatus("APPROVED");
        adjustment.setReversalTransactionId(reversal.getTransactionId());
        if (original.getPenaltyCaseId() != null) {
            refreshPenaltyCase(original.getPenaltyCaseId());
        }
        if (wallet.getTotalDebt().signum() == 0) {
            tryRestoreBorrowingRights(original.getUserId(), LocalDateTime.now());
        }
        return adjustmentRepository.save(adjustment);
    }

    @Transactional(readOnly = true)
    public List<TransactionEntity> getUserLedger(UUID userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public List<TransactionAdjustmentEntity> getAdjustments() {
        return adjustmentRepository.findAllByOrderByCreatedAtDesc();
    }

    private PaymentReceiptDTO buildReceipt(TransactionEntity payment, UUID userId) {
        List<PaymentReceiptDTO.AllocationLine> lines = allocationRepository
                .findByPaymentTransactionIdOrderByAllocationId(payment.getTransactionId()).stream()
                .map(allocation -> {
                    TransactionEntity debt = transactionRepository.findById(allocation.getDebtTransactionId())
                            .orElse(null);
                    return PaymentReceiptDTO.AllocationLine.builder()
                            .debtTransactionId(allocation.getDebtTransactionId())
                            .feeType(debt == null ? null : debt.getType())
                            .reason(debt == null ? null : debt.getReason())
                            .amount(allocation.getAmount())
                            .build();
                })
                .toList();
        BigDecimal remainingDebt = walletRepository.findByUserId(userId)
                .map(wallet -> zero(wallet.getTotalDebt()))
                .orElse(BigDecimal.ZERO);
        return PaymentReceiptDTO.builder()
                .paymentTransactionId(payment.getTransactionId())
                .receiptNumber(payment.getReceiptNumber())
                .userId(payment.getUserId())
                .totalAmount(payment.getAmount().abs())
                .paymentMethod(payment.getPaymentMethod())
                .confirmedBy(payment.getConfirmedBy())
                .paidAt(payment.getPaidAt())
                .remainingDebt(remainingDebt)
                .allocations(lines)
                .build();
    }

    private void refreshPenaltyCase(Long caseId) {
        PenaltyCaseEntity penaltyCase = penaltyCaseRepository.findByIdForUpdate(caseId).orElse(null);
        if (penaltyCase == null) {
            return;
        }
        BigDecimal satisfied = transactionRepository.findByPenaltyCaseIdOrderByCreatedAtAsc(caseId).stream()
                .filter(entry -> entry.getAmount() != null && entry.getAmount().signum() > 0)
                .map(entry -> zero(entry.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        penaltyCase.setPaidAmount(satisfied.min(penaltyCase.getTotalAmount()));
        if (penaltyCase.getPaidAmount().compareTo(penaltyCase.getTotalAmount()) >= 0
                && !"PENDING_REVIEW".equals(penaltyCase.getStatus())) {
            if ("REPLACEMENT".equals(penaltyCase.getSettlementMethod())
                    && penaltyCase.getReplacementAcceptedAt() == null) {
                penaltyCase.setStatus("WAITING_REPLACEMENT");
            } else {
                penaltyCase.setStatus("PAID");
                closeResolvedLoanDetail(penaltyCase);
            }
        }
        penaltyCaseRepository.save(penaltyCase);
    }

    private void closeResolvedLoanDetail(PenaltyCaseEntity penaltyCase) {
        LoanDetailEntity detail = detailRepository.findById(penaltyCase.getLoanDetailId()).orElse(null);
        if (detail == null) {
            return;
        }
        detail.setReturnDate(LocalDateTime.now());
        detail.setReturnStatus("SETTLED");
        detailRepository.save(detail);
        LoanTicketEntity ticket = ticketRepository.findById(penaltyCase.getTicketId()).orElse(null);
        if (ticket != null && detailRepository.findByTicketId(ticket.getTicketId()).stream()
                .allMatch(item -> item.getReturnDate() != null || "CANCELLED".equals(item.getReturnStatus()))) {
            ticket.setStatus("RETURNED");
            ticketRepository.save(ticket);
        }
    }

    private void tryRestoreBorrowingRights(UUID userId, LocalDateTime now) {
        UserEntity user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null || user.getCirculationLockedUntil() == null
                || user.getCirculationLockedUntil().isAfter(now)) {
            return;
        }
        if (detailRepository.countOpenItemsByUserId(
                userId, List.of("BORROWING", "OVERDUE")) > 0) {
            return;
        }
        user.setCirculationLockedUntil(null);
        user.setCirculationLockReason(null);
        userRepository.save(user);
        notificationService.createOnce(
                userId, "CIRCULATION_UNLOCKED",
                "Quyền mượn, đặt giữ và gia hạn đã được khôi phục.",
                "USER", userId.toString(),
                "CIRCULATION_UNLOCKED:" + now.toLocalDate(), now);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
