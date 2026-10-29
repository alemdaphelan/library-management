package com.huit.library.modules.auth.dto;

import lombok.Data;

@Data
public class LogoutRequestDTO {
    private String refreshToken;
}
