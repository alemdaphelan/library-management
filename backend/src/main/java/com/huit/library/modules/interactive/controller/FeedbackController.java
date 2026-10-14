package com.huit.library.modules.interactive.controller;

import com.huit.library.modules.interactive.entity.BookProposalEntity;
import com.huit.library.modules.interactive.repository.BookProposalRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/feedback", "/api/v1/feedback"})
@RequiredArgsConstructor
@Tag(name = "8. Interactive (Complaints, Proposals)", description = "Endpoints for complaints and book proposals")
public class FeedbackController {

    private final BookProposalRepository bookProposalRepository;

    @PostMapping("/complaints")
    @Operation(summary = "Submit Complaint", description = "Submit a user complaint to the library admin.")
    public ResponseEntity<?> submitComplaint() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/proposals")
    @Operation(summary = "Submit Proposal", description = "Propose the library buys a new book.")
    public ResponseEntity<?> submitProposal(@RequestBody Map<String, Object> payload) {
        BookProposalEntity proposal = new BookProposalEntity();
        proposal.setTitle((String) payload.get("title"));
        proposal.setAuthorName((String) payload.get("authorName"));
        proposal.setReason((String) payload.get("reason"));
        proposal.setStatus("PENDING");

        Object userIdObj = payload.get("userId");
        if (userIdObj != null) {
            try {
                proposal.setUserId(UUID.fromString(userIdObj.toString()));
            } catch (Exception ignored) {
            }
        }
        if (proposal.getUserId() == null) {
            proposal.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        }

        BookProposalEntity saved = bookProposalRepository.save(proposal);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/proposals")
    @Operation(summary = "Get All Proposals", description = "Retrieve list of all book proposals.")
    public ResponseEntity<?> getProposals() {
        return ResponseEntity.ok(bookProposalRepository.findAll());
    }
}
