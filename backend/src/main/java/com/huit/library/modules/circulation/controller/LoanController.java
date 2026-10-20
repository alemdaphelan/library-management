package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.BorrowRequestDTO;
import com.huit.library.modules.circulation.dto.BorrowResponseDTO;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnRequestDTO;
import com.huit.library.modules.circulation.dto.LoanStateResponseDTO;
import com.huit.library.modules.circulation.dto.RejectLoanRequestDTO;
import com.huit.library.modules.circulation.service.CirculationStateService;
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
    private final CirculationStateService circulationStateService;

    public LoanController(LoanService loanService, CirculationStateService circulationStateService) {
        this.loanService = loanService;
        this.circulationStateService = circulationStateService;
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

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "List loan tickets for staff", description = "Returns real circulation tickets for the librarian management screen.")
    public ResponseEntity<?> getStaffLoans() {
        return ResponseEntity.ok(loanService.getStaffLoans());
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get loan ticket detail", description = "Returns reader and copy details required for reservation review at the circulation desk.")
    public ResponseEntity<?> getLoan(@PathVariable Long ticketId) {
        return ResponseEntity.ok(loanService.getLoan(ticketId));
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
    @Operation(summary = "Renew Loan", description = "Reader renews a currently borrowed book, extending due date by 10 days.")
    public ResponseEntity<?> renewLoan(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getUserId();
        return ResponseEntity.ok(loanService.renewLoan(ticketId, userId));
    }

    @PostMapping("/renew/{ticketId}/items/{detailId}")
    @Operation(summary = "Renew one loan item", description = "Renews exactly one borrowed item by 10 days from its current due date.")
    public ResponseEntity<?> renewLoanItem(
            @PathVariable Long ticketId,
            @PathVariable Long detailId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(loanService.renewLoan(
                ticketId, detailId, userDetails.getUser().getUserId()));
    }

    @PostMapping("/{ticketId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Approve reservation", description = "Moves a pending reservation to RESERVED and records the staff transition.")
    public ResponseEntity<LoanStateResponseDTO> approveReservation(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(circulationStateService.approveReservation(
                ticketId, userDetails.getUser().getUserId()));
    }

    @PostMapping("/{ticketId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Reject reservation", description = "Rejects a pending or reserved request, records the reason and releases its copies.")
    public ResponseEntity<LoanStateResponseDTO> rejectReservation(
            @PathVariable Long ticketId,
            @Valid @RequestBody RejectLoanRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(circulationStateService.rejectReservation(
                ticketId, userDetails.getUser().getUserId(), request.getReason()));
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Counter Checkout", description = "Librarian processes book checkout at the desk using barcodes.")
    public ResponseEntity<?> checkoutBooks(
            @Valid @RequestBody CheckoutRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(circulationStateService.checkout(
                request, userDetails.getUser().getUserId()));
    }

    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Counter Return", description = "Librarian processes book returns at the desk.")
    public ResponseEntity<?> returnBooks(
            @Valid @RequestBody ReturnRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(circulationStateService.returnBooks(
                request, userDetails.getUser().getUserId()));
    }
}
