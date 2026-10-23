package com.huit.library.modules.interactive.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BookReviewSummaryDTO {
    private Long bookId;
    private double averageRating;
    private long reviewCount;
    private BookReviewDTO myReview;
    private List<BookReviewDTO> reviews;
}

