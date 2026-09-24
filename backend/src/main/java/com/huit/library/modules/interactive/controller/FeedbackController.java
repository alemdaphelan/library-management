package com.huit.library.modules.interactive.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feedback")
@Tag(name = "8. Interactive (Complaints, Proposals)", description = "Endpoints for complaints and book proposals")
public class FeedbackController {

    @PostMapping("/complaints")
    @Operation(summary = "Submit Complaint", description = "Submit a user complaint to the library admin.")
    public ResponseEntity<?> submitComplaint() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/proposals")
    @Operation(summary = "Submit Proposal", description = "Propose the library buys a new book.")
    public ResponseEntity<?> submitProposal() {
        return ResponseEntity.ok().build();
    }
}
