package com.huit.library.modules.circulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waitlist")
@Tag(name = "5. Waitlist", description = "Endpoints for joining queue for borrowed books")
public class WaitlistController {

    @PostMapping("/join")
    @Operation(summary = "Join Waitlist", description = "Join queue for a borrowed book.")
    public ResponseEntity<?> joinWaitlist() {
        return ResponseEntity.ok().build();
    }
}
