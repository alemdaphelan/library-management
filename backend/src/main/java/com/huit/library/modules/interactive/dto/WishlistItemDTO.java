package com.huit.library.modules.interactive.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WishlistItemDTO {
    private UUID wishlistId;
    private Long bookId;
    private String title;
    private String author;
    private String category;
    private String cover;
    private long availableCopies;
    private LocalDateTime addedAt;
}

