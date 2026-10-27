package com.huit.library.modules.auth.controller;

import com.huit.library.modules.auth.dto.JwtResponseDTO;
import com.huit.library.modules.auth.dto.LoginRequestDTO;
import com.huit.library.modules.auth.dto.LogoutRequestDTO;
import com.huit.library.modules.auth.dto.ChangePasswordRequestDTO;
import com.huit.library.modules.auth.dto.ForgotPasswordRequestDTO;
import com.huit.library.modules.auth.dto.RefreshTokenRequestDTO;
import com.huit.library.modules.auth.dto.ResetPasswordRequestDTO;
import com.huit.library.modules.auth.dto.VerifyOtpRequestDTO;
import com.huit.library.modules.auth.exception.OtpDeliveryException;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.service.AuthService;
import com.huit.library.core.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Email/MSSV hoặc mật khẩu không chính xác");
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Blacklist the current JWT token in Redis.")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
                                    @RequestBody(required = false) LogoutRequestDTO request) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            tokenService.blacklistToken(jwt);
        }
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            tokenService.deleteRefreshToken(request.getRefreshToken());
        }
        return ResponseEntity.ok().body("User logged out successfully!");
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Get a new JWT and refresh token using a valid refresh token.")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequestDTO request) {
        try {
            JwtResponseDTO response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Refresh token không hợp lệ hoặc đã hết hạn");
        }
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change Password", description = "Change user password.")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @Valid @RequestBody ChangePasswordRequestDTO request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            authService.changePassword(userDetails.getUser().getUserId(),
                    request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok().body("Password changed successfully!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot Password", description = "Send OTP to user email")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        try {
            authService.forgotPassword(request.getEmail().trim());
            return ResponseEntity.ok().body("Nếu tài khoản tồn tại, mã OTP sẽ được gửi đến email đã đăng ký.");
        } catch (OtpDeliveryException e) {
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify the OTP sent to email")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        try {
            authService.verifyOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok().body("Xác thực OTP thành công.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset Password", description = "Set new password using OTP")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        try {
            authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
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
            return ResponseEntity.ok(authService.getFreshUser(userDetails.getUser().getUserId()));
        }
        return ResponseEntity.status(401).body("Unauthorized");
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Update full name, phone, or avatar")
    public ResponseEntity<?> updateProfile(org.springframework.security.core.Authentication authentication,
                                           @Valid @RequestBody com.huit.library.modules.auth.dto.UpdateProfileRequestDTO request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        if (authentication.getPrincipal() instanceof com.huit.library.core.security.CustomUserDetails) {
            com.huit.library.core.security.CustomUserDetails userDetails = (com.huit.library.core.security.CustomUserDetails) authentication.getPrincipal();
            return ResponseEntity.ok(authService.updateProfile(userDetails.getUser().getUserId(), request));
        }
        return ResponseEntity.status(401).body("Unauthorized");
    }

    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload user avatar", description = "Upload image file as avatar")
    public ResponseEntity<?> uploadAvatar(org.springframework.security.core.Authentication authentication,
                                          @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        if (authentication.getPrincipal() instanceof com.huit.library.core.security.CustomUserDetails) {
            com.huit.library.core.security.CustomUserDetails userDetails = (com.huit.library.core.security.CustomUserDetails) authentication.getPrincipal();
            try {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui lòng chọn tệp ảnh hợp lệ");
                }
                String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
                String base64 = java.util.Base64.getEncoder().encodeToString(file.getBytes());
                String dataUrl = "data:" + contentType + ";base64," + base64;
                
                com.huit.library.modules.auth.dto.UpdateProfileRequestDTO req = new com.huit.library.modules.auth.dto.UpdateProfileRequestDTO();
                req.setAvatarUrl(dataUrl);
                UserEntity updated = authService.updateProfile(userDetails.getUser().getUserId(), req);
                return ResponseEntity.ok(updated);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Lỗi tải ảnh lên: " + e.getMessage());
            }
        }
        return ResponseEntity.status(401).body("Unauthorized");
    }
}
