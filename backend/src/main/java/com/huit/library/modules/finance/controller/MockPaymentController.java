package com.huit.library.modules.finance.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.finance.dto.MockPaymentModels;
import com.huit.library.modules.finance.dto.OnlinePaymentSessionDTO;
import com.huit.library.modules.finance.service.MockPaymentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mock-payments/sessions")
@Profile("(dev | test) & !prod & !production")
@ConditionalOnProperty(name = "library.payment.mock.enabled", havingValue = "true")
@PreAuthorize("hasRole('STUDENT')")
public class MockPaymentController {
    private final MockPaymentService payments;

    public MockPaymentController(MockPaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public OnlinePaymentSessionDTO create(
            @Valid @RequestBody MockPaymentModels.Create request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return payments.create(user.getUser().getUserId(), request);
    }

    @GetMapping("/{reference}")
    public OnlinePaymentSessionDTO get(
            @PathVariable String reference, @AuthenticationPrincipal CustomUserDetails user) {
        return payments.get(user.getUser().getUserId(), reference);
    }

    @PostMapping("/{reference}/finish")
    public OnlinePaymentSessionDTO finish(
            @PathVariable String reference,
            @Valid @RequestBody MockPaymentModels.Finish request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return payments.finish(user.getUser().getUserId(), reference, request.outcome());
    }
}
