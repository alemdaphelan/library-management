package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.LoanItemDTO;
import com.huit.library.modules.circulation.dto.LoanLossReportRequestDTO;
import com.huit.library.modules.circulation.dto.LoanTicketDetailDTO;
import com.huit.library.modules.circulation.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/loans")
public class LoanLossReportController {
    private final LoanService loans;

    public LoanLossReportController(LoanService loans) {
        this.loans = loans;
    }

    @PostMapping("/{ticketId}/items/{detailId}/loss-report")
    @PreAuthorize("hasRole('STUDENT')")
    public LoanItemDTO report(
            @PathVariable Long ticketId,
            @PathVariable Long detailId,
            @Valid @RequestBody LoanLossReportRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails reader) {
        return loans.reportLoss(
                ticketId, detailId, request.description(), reader.getUser().getUserId());
    }

    @GetMapping("/loss-reports/{detailId}")
    @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public LoanTicketDetailDTO getReport(@PathVariable Long detailId) {
        return loans.getLossReport(detailId);
    }
}
