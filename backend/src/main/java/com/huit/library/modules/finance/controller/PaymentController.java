package com.huit.library.modules.finance.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.finance.dto.AdjustmentRequestDTO;
import com.huit.library.modules.finance.dto.AdjustmentReviewRequestDTO;
import com.huit.library.modules.finance.dto.CashDepositReceiptDTO;
import com.huit.library.modules.finance.dto.CashDepositRequestDTO;
import com.huit.library.modules.finance.dto.CounterDepositRequestDTO;
import com.huit.library.modules.finance.dto.DebtPaymentRequestDTO;
import com.huit.library.modules.finance.service.DebtLedgerService;
import com.huit.library.modules.circulation.service.LibraryHoursService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class PaymentController {
    private final DebtLedgerService debtLedgerService;
    private final LibraryHoursService libraryHoursService;

    public PaymentController(DebtLedgerService debtLedgerService, LibraryHoursService libraryHoursService) {
        this.debtLedgerService = debtLedgerService;
        this.libraryHoursService = libraryHoursService;
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> recordPayment(
            @Valid @RequestBody DebtPaymentRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        libraryHoursService.requireCounterOpen("ghi nhận thanh toán");
        return ResponseEntity.ok(debtLedgerService.recordPayment(
                request, operator.getUser().getUserId()));
    }

    @PostMapping("/deposit-topup")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> recordDepositTopup(
            @Valid @RequestBody CounterDepositRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return ResponseEntity.ok(debtLedgerService.recordCounterDepositTopUp(
                request.getUserId(),
                request.getAmount(),
                request.getMethod(),
                operator.getUser().getUserId(),
                request.getIdempotencyKey(),
                request.getNote()));
    }

    @GetMapping("/ledger/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> getLedger(@PathVariable UUID userId) {
        return ResponseEntity.ok(debtLedgerService.getUserLedger(userId));
    }

    @PostMapping("/cash-deposits")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public CashDepositReceiptDTO recordCashDeposit(
            @Valid @RequestBody CashDepositRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return debtLedgerService.recordCashDeposit(request, operator.getUser().getUserId());
    }

    @GetMapping("/my-ledger")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getMyLedger(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(debtLedgerService.getUserLedger(
                user.getUser().getUserId()));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> requestAdjustment(
            @Valid @RequestBody AdjustmentRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return ResponseEntity.ok(debtLedgerService.requestAdjustment(
                request, operator.getUser().getUserId()));
    }

    @PatchMapping("/adjustments/{adjustmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reviewAdjustment(
            @PathVariable Long adjustmentId,
            @Valid @RequestBody AdjustmentReviewRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(debtLedgerService.reviewAdjustment(
                adjustmentId, request, admin.getUser().getUserId()));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAdjustments() {
        return ResponseEntity.ok(debtLedgerService.getAdjustments());
    }
}
