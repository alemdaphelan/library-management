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
    private String department;
    private String cover;
    private String location;
    private long copies;
    private long availableCopies;
    private String status;
    private String isbn;
    private String publisher;
    private Integer publishYear;
    private Boolean isDigital;
    private LocalDateTime addedAt;
}
