package com.huit.library.modules.auth.dto;

import lombok.Data;

@Data
public class UpdateProfileRequestDTO {
    private String fullName;
    private String phone;
    private String avatarUrl;
}
