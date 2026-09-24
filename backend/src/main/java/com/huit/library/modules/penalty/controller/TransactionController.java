package com.huit.library.modules.penalty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@Tag(name = "6. Penalty, Wallet & Finance", description = "Endpoints for managing fine payments and wallet refunds")
public class TransactionController {

    @PostMapping("/pay")
    @Operation(summary = "Pay Penalty", description = "Process penalty fine payment.")
    public ResponseEntity<?> payPenalty() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refund")
    @Operation(summary = "Refund Deposit", description = "Refund deposit to User Wallet.")
    public ResponseEntity<?> refundDeposit() {
        return ResponseEntity.ok().build();
    }
}
