package com.huit.library.modules.circulation.repository;

import java.time.LocalDateTime;

public interface BookPopularityProjection {
    Long getBookId();
    long getBorrowCount();
    double getWeightedScore();
    LocalDateTime getLastBorrowedAt();
}
