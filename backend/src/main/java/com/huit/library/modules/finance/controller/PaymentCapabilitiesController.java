package com.huit.library.modules.finance.controller;

import com.huit.library.modules.finance.service.MockPaymentService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/online-payments/capabilities")
public class PaymentCapabilitiesController {
    private final ObjectProvider<MockPaymentService> mock;
    public PaymentCapabilitiesController(ObjectProvider<MockPaymentService> mock) { this.mock = mock; }
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Boolean> get() { return Map.of("mockEnabled", mock.getIfAvailable() != null); }
}
