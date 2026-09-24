package com.huit.library.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "1. Authentication", description = "Endpoints for login, logout and token management")
public class AuthController {

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and receive a JWT Bearer token.")
    public ResponseEntity<?> login() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Blacklist the current JWT token in Redis.")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok().build();
    }
}