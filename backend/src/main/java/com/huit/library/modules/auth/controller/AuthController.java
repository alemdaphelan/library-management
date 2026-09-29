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
@RequestMapping("/api/v1/auth")
@Tag(name = "1. Authentication", description = "Endpoints for login, logout and token management")
public class AuthController {

    private final AuthService authService;
    private final com.huit.library.core.security.TokenService tokenService;

    public AuthController(AuthService authService, com.huit.library.core.security.TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and receive a JWT Bearer token.")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        try {
            JwtResponseDTO response = authService.authenticateUser(loginRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Blacklist the current JWT token in Redis.")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            tokenService.blacklistToken(jwt);
        }
        return ResponseEntity.ok().body("User logged out successfully!");
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Get a new JWT and refresh token using a valid refresh token.")
    public ResponseEntity<JwtResponseDTO> refreshToken(@RequestParam String refreshToken) {
        JwtResponseDTO response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change Password", description = "Change user password.")
    public ResponseEntity<?> changePassword(@RequestParam String email, @RequestParam String oldPassword, @RequestParam String newPassword) {
        try {
            authService.changePassword(email, oldPassword, newPassword);
            return ResponseEntity.ok().body("Password changed successfully!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot Password", description = "Send OTP to user email")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        try {
            authService.forgotPassword(email);
            return ResponseEntity.ok().body("Mã OTP đã được gửi đến email của bạn.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify the OTP sent to email")
    public ResponseEntity<?> verifyOtp(@RequestParam String email, @RequestParam String otp) {
        try {
            authService.verifyOtp(email, otp);
            return ResponseEntity.ok().body("Xác thực OTP thành công.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset Password", description = "Set new password using OTP")
    public ResponseEntity<?> resetPassword(@RequestParam String email, @RequestParam String otp, @RequestParam String newPassword) {
        try {
            authService.resetPassword(email, otp, newPassword);
            return ResponseEntity.ok().body("Đặt lại mật khẩu thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Retrieve current authenticated user information")
    public ResponseEntity<?> getCurrentUser(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        if (authentication.getPrincipal() instanceof com.huit.library.core.security.CustomUserDetails) {
            com.huit.library.core.security.CustomUserDetails userDetails = (com.huit.library.core.security.CustomUserDetails) authentication.getPrincipal();
            return ResponseEntity.ok(userDetails.getUser());
        }
        return ResponseEntity.status(401).body("Unauthorized");
    }
}