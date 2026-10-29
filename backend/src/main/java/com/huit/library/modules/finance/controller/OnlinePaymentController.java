package com.huit.library.modules.finance.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.finance.dto.OnlinePaymentCreateRequestDTO;
import com.huit.library.modules.finance.dto.OnlinePaymentSessionDTO;
import com.huit.library.modules.finance.service.OnlinePaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/online-payments")
public class OnlinePaymentController {
    private final OnlinePaymentService onlinePaymentService;

    public OnlinePaymentController(OnlinePaymentService onlinePaymentService) {
        this.onlinePaymentService = onlinePaymentService;
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OnlinePaymentSessionDTO> create(
            @Valid @RequestBody OnlinePaymentCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest httpRequest) {
        String forwarded = httpRequest.getHeader("X-Forwarded-For");
        String clientIp = forwarded == null ? httpRequest.getRemoteAddr() : forwarded;
        return ResponseEntity.status(HttpStatus.CREATED).body(onlinePaymentService.create(
                principal.getUser().getUserId(), request, clientIp));
    }

    @GetMapping("/sessions/{reference}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OnlinePaymentSessionDTO> get(
            @PathVariable String reference,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(onlinePaymentService.get(principal.getUser().getUserId(), reference));
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        OnlinePaymentSessionDTO result = onlinePaymentService.processCallback(params);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(onlinePaymentService.frontendRedirect(result)).toString())
                .build();
    }

    @GetMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        Map<String, String> response = new LinkedHashMap<>();
        try {
            onlinePaymentService.processCallback(params);
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            response.put("RspCode", ex.getStatusCode().value() == 404 ? "01" : "97");
            response.put("Message", ex.getReason() == null ? "Invalid request" : ex.getReason());
            return ResponseEntity.ok(response);
        }
    }
}
