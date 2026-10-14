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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/proposals")
@Tag(name = "Book Proposals", description = "Endpoints for recommending/proposing new books to library")
public class BookProposalController {

    private final BookProposalService proposalService;

    public BookProposalController(BookProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @PostMapping
    @Operation(summary = "Submit book proposal", description = "Student/reader submits a recommendation to buy new book")
    public ResponseEntity<?> submitProposal(Authentication authentication, @RequestBody CreateProposalRequestDTO req) {
        UUID userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            userId = userDetails.getUser().getUserId();
        }

        try {
            ProposalResponseDTO result = proposalService.createProposal(userId, req);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my")
    @Operation(summary = "Get my proposals", description = "Reader views their submitted proposals and approval status")
    public ResponseEntity<?> getMyProposals(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).body("Vui lòng đăng nhập để xem lịch sử đề xuất");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
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
    public ResponseEntity<?> updateProposalStatus(@PathVariable Long id, @RequestBody UpdateProposalStatusDTO req) {
        try {
            ProposalResponseDTO result = proposalService.updateStatus(id, req);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
