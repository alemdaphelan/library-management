package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.QrPayloadResponseDTO;
import com.huit.library.modules.circulation.dto.QrResolveRequestDTO;
import com.huit.library.modules.circulation.dto.QrResolveResponseDTO;
import com.huit.library.modules.circulation.service.LibrarianQrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qr")
@Tag(name = "Librarian QR", description = "Signed, expiring QR payloads for circulation desk workflows")
public class LibrarianQrController {
    private final LibrarianQrService qrService;

    public LibrarianQrController(LibrarianQrService qrService) {
        this.qrService = qrService;
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Create a short-lived signed QR for a loan ticket")
    public ResponseEntity<QrPayloadResponseDTO> issueTicketQr(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(qrService.issueTicketPayload(ticketId, userDetails.getUser()));
    }

    @GetMapping("/cards/me")
    @Operation(summary = "Create a short-lived signed QR for the current library card")
    public ResponseEntity<QrPayloadResponseDTO> issueMyCardQr(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(qrService.issueCardPayload(userDetails.getUser()));
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Validate and resolve a signed QR at the circulation desk")
    public ResponseEntity<QrResolveResponseDTO> resolve(@Valid @RequestBody QrResolveRequestDTO request) {
        return ResponseEntity.ok(qrService.resolve(request.getPayload()));
    }
}
