package com.huit.library.modules.finance.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.finance.dto.AdjustmentRequestDTO;
import com.huit.library.modules.finance.dto.AdjustmentReviewRequestDTO;
import com.huit.library.modules.finance.dto.DebtPaymentRequestDTO;
import com.huit.library.modules.finance.service.DebtLedgerService;
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

    public PaymentController(DebtLedgerService debtLedgerService) {
        this.debtLedgerService = debtLedgerService;
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> recordPayment(
            @Valid @RequestBody DebtPaymentRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return ResponseEntity.ok(debtLedgerService.recordPayment(
                request, operator.getUser().getUserId()));
    }

    @GetMapping("/ledger/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> getLedger(@PathVariable UUID userId) {
        return ResponseEntity.ok(debtLedgerService.getUserLedger(userId));
    }

    @GetMapping("/my-ledger")
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
