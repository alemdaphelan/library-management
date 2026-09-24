package com.huit.library.modules.auth.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data @AllArgsConstructor public class JwtResponseDTO { 
    private String accessToken; 
    private String refreshToken; 
    private String role; 
}