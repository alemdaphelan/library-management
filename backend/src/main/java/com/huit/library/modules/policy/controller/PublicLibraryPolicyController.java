package com.huit.library.modules.policy.controller;

import com.huit.library.modules.policy.dto.LibraryPolicyPublicResponseDTO;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/library-policy")
public class PublicLibraryPolicyController {
    private final LibraryPolicyService service;

    public PublicLibraryPolicyController(LibraryPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public LibraryPolicyPublicResponseDTO getPolicy() {
        return service.getPublicPolicy();
    }
}
