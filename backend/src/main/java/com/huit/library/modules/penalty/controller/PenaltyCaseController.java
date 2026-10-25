package com.huit.library.modules.penalty.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.penalty.dto.CompensationRateRequestDTO;
import com.huit.library.modules.penalty.dto.PenaltyCaseRequestDTO;
import com.huit.library.modules.penalty.dto.ReplacementAcceptanceRequestDTO;
import com.huit.library.modules.penalty.dto.WaiverReviewRequestDTO;
import com.huit.library.modules.penalty.service.PenaltyCaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/penalty-cases")
public class PenaltyCaseController {
    private final PenaltyCaseService penaltyCaseService;

    public PenaltyCaseController(PenaltyCaseService penaltyCaseService) {
        this.penaltyCaseService = penaltyCaseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> createCase(
            @Valid @RequestBody PenaltyCaseRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return ResponseEntity.ok(penaltyCaseService.createCase(
                request, operator.getUser().getUserId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> listCases() {
        return ResponseEntity.ok(penaltyCaseService.listCases());
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listMyCases(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(penaltyCaseService.listMyCases(user.getUser().getUserId()));
    }

    @PostMapping("/{caseId}/review-request")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> submitForReview(
            @PathVariable Long caseId,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return ResponseEntity.ok(penaltyCaseService.submitForReview(
                caseId, operator.getUser().getUserId()));
    }

    @PatchMapping("/{caseId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reviewWaiver(
            @PathVariable Long caseId,
            @Valid @RequestBody WaiverReviewRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(penaltyCaseService.reviewWaiver(
                caseId, request, admin.getUser().getUserId()));
    }

    @PatchMapping("/{caseId}/replacement-acceptance")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> acceptReplacement(
            @PathVariable Long caseId,
            @Valid @RequestBody ReplacementAcceptanceRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return ResponseEntity.ok(penaltyCaseService.acceptReplacement(
                caseId, request, operator.getUser().getUserId()));
    }

    @PostMapping("/rates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createRate(
            @Valid @RequestBody CompensationRateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(penaltyCaseService.createRate(
                request, admin.getUser().getUserId()));
    }
}
