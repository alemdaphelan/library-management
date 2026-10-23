package com.huit.library.modules.interactive.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/feedback", "/api/v1/feedback"})
@Tag(name = "8. Interactive Feedback", description = "Public feedback endpoints")
public class FeedbackController {

    @PostMapping("/complaints")
    @Operation(summary = "Submit Complaint", description = "Submit a user complaint to the library admin.")
    public ResponseEntity<?> submitComplaint() {
        return ResponseEntity.ok().build();
    }

}
