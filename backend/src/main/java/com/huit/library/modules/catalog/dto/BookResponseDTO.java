package com.huit.library.modules.catalog.dto;

import com.huit.library.modules.catalog.entity.CategoryEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BookResponseDTO {
    private Long bookId;
    private String title;
    private String isbn;
    private String ddcCallNumber;
    private Integer publishYear;
    private String language;
    private String labelColor;
    private BigDecimal defaultPrice;
    private String priceCurrency;
    private String priceSource;
    private LocalDateTime priceUpdatedAt;
    private String priceStatus;
    private String materialType;
    private Boolean isDigital;
    private String author;
    private String publisher;
    private String description;
    private CategoryEntity category;
    private String department;
    private String imageUrlS;
    private String imageUrlM;
    private String imageUrlL;
    private long copies;
    private long availableCopies;
    private String location;
    private String status;
    private double rating;
    private long reviewsCount;
    private long borrowCount;
    private long viewCount;
}
