package com.huit.library.modules.interactive.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.interactive.dto.CreateProposalRequestDTO;
import com.huit.library.modules.interactive.dto.ProposalResponseDTO;
import com.huit.library.modules.interactive.dto.UpdateProposalStatusDTO;
import com.huit.library.modules.interactive.service.BookProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/proposals")
@Tag(name = "Book Proposals", description = "Endpoints for recommending/proposing new books to library")
public class BookProposalController {

    private final BookProposalService proposalService;

    public BookProposalController(BookProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit book proposal", description = "Student/reader submits a recommendation to buy new book")
    public ResponseEntity<ProposalResponseDTO> submitProposal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateProposalRequestDTO req) {
        return ResponseEntity.ok(proposalService.createProposal(
                userDetails.getUser().getUserId(), req));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my proposals", description = "Reader views their submitted proposals and approval status")
    public ResponseEntity<List<ProposalResponseDTO>> getMyProposals(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<ProposalResponseDTO> list = proposalService.getMyProposals(userDetails.getUser().getUserId());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get all proposals for admin", description = "Librarian/admin views all proposals submitted by readers")
    public ResponseEntity<?> getAllProposalsForAdmin() {
        return ResponseEntity.ok(proposalService.getAllProposals());
    }

    @PutMapping("/admin/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Update proposal status", description = "Librarian/admin approves or rejects a proposal")
    public ResponseEntity<?> updateProposalStatus(@PathVariable Long id, @Valid @RequestBody UpdateProposalStatusDTO req) {
        try {
            ProposalResponseDTO result = proposalService.updateStatus(id, req);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
