package com.huit.library.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponseDTO {
    @Builder.Default
    private String tokenType = "Bearer";
    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private String fullName;
    private String email;
    private String mssv;
    private String role;
    private Boolean isFirstLogin;
    private String avatarUrl;
    private String cardStatus;

    public JwtResponseDTO(String accessToken, String refreshToken, String role) {
        this.tokenType = "Bearer";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.role = role;
    }
}