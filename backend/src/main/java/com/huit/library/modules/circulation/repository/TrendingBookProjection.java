package com.huit.library.modules.circulation.repository;

public interface TrendingBookProjection {
    Long getBookId();
    String getTitle();
    String getAuthor();
    long getBorrows();
}
