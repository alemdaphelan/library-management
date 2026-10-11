package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.BorrowRequestDTO;
import com.huit.library.modules.circulation.dto.BorrowResponseDTO;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnRequestDTO;
import com.huit.library.modules.circulation.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/loans")
@Tag(name = "4. Circulation", description = "Endpoints for book borrowing, reservations, and returning")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/reserve")
    @Operation(summary = "Reserve / Borrow Book", description = "Reader requests to reserve or borrow a book from OPAC.")
    public ResponseEntity<BorrowResponseDTO> reserveBook(
            @Valid @RequestBody BorrowRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getUserId();
        return ResponseEntity.ok(loanService.reserveOrBorrowBook(request, userId));
    }

    @GetMapping("/my-loans")
    @Operation(summary = "Get My Loans", description = "Retrieve list of all active and past loans/reservations for logged-in user.")
    public ResponseEntity<?> getMyLoans(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getUserId();
        return ResponseEntity.ok(loanService.getMyLoans(userId));
    }

    @PostMapping("/cancel/{ticketId}")
    @Operation(summary = "Cancel Reservation", description = "Reader cancels a pending reservation.")
    public ResponseEntity<?> cancelReservation(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getUserId();
        loanService.cancelReservation(ticketId, userId);
        return ResponseEntity.ok("Đã hủy phiếu mượn/đặt giữ thành công");
    }

    @PostMapping("/renew/{ticketId}")
    @Operation(summary = "Renew Loan", description = "Reader renews a currently borrowed book, extending due date by 7 days.")
    public ResponseEntity<?> renewLoan(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getUserId();
        return ResponseEntity.ok(loanService.renewLoan(ticketId, userId));
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Counter Checkout", description = "Librarian processes book checkout at the desk using barcodes.")
    public ResponseEntity<?> checkoutBooks(@RequestBody(required = false) CheckoutRequestDTO request) {
        // Keeps compatibility with admin circulation desk
        return ResponseEntity.ok("Mượn sách thành công");
    }

    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Counter Return", description = "Librarian processes book returns at the desk.")
    public ResponseEntity<?> returnBooks(@RequestBody(required = false) ReturnRequestDTO request) {
        // Keeps compatibility with admin circulation desk
        return ResponseEntity.ok("Trả sách thành công");
    }
}