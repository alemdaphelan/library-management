package com.huit.library.modules.policy.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.policy.dto.LibraryPolicyRequestDTO;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/library-policy")
@PreAuthorize("hasRole('ADMIN')")
public class LibraryPolicyController {
    private final LibraryPolicyService service;

    public LibraryPolicyController(LibraryPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public Object getPolicy() {
        return service.getPolicy();
    }

    @GetMapping("/history")
    public Object getHistory() {
        return service.getHistory();
    }

    @PutMapping
    public Object updatePolicy(
            @Valid @RequestBody LibraryPolicyRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.updatePolicy(request, principal.getUser().getUserId());
    }
}
