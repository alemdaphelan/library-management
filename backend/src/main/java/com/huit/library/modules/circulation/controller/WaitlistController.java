package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waitlist")
@Tag(name = "5. Waitlist", description = "Endpoints for joining queue for borrowed books")
public class WaitlistController {
    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping("/join")
    @Operation(summary = "Join Waitlist", description = "Join queue for a borrowed book.")
    public ResponseEntity<?> joinWaitlist(
            @RequestParam Long bookId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(waitlistService.join(bookId, user.getUser().getUserId()));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyWaitlist(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(waitlistService.getMine(user.getUser().getUserId()));
    }
}
