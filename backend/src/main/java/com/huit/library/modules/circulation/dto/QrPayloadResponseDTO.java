package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class QrPayloadResponseDTO {
    private String payload;
    private Instant expiresAt;
}
