package com.huit.library.modules.interactive.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WishlistRequestDTO {
    @NotNull(message = "Mã đầu sách không được để trống")
    private Long bookId;
}
