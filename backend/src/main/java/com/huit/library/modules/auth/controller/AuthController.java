package com.huit.library.modules.auth.controller;

import com.huit.library.modules.auth.dto.JwtResponseDTO;
import com.huit.library.modules.auth.dto.LoginRequestDTO;
import com.huit.library.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Tag(name = "1. Authentication", description = "Endpoints for login, logout and token management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and receive a JWT Bearer token.")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        JwtResponseDTO response = authService.authenticateUser(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Blacklist the current JWT token in Redis.")
    public ResponseEntity<?> logout() {
        // Implement token blacklisting via Redis here later
        return ResponseEntity.ok().body("User logged out successfully!");
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change Password", description = "Change user password.")
    public ResponseEntity<?> changePassword(@RequestParam String email, @RequestParam String oldPassword, @RequestParam String newPassword) {
        authService.changePassword(email, oldPassword, newPassword);
        return ResponseEntity.ok().body("Password changed successfully!");
    }
}