package com.huit.library.modules.catalog.dto;

import com.huit.library.modules.catalog.entity.CategoryEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

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
    private Boolean isDigital;
    private String author;
    private String publisher;
    private String description;
    private CategoryEntity category;
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
