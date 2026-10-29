package com.huit.library.modules.circulation.repository;

import java.time.LocalDateTime;

public interface UserBookInteractionProjection {
    Long getBookId();
    long getBorrowCount();
    long getRenewalCount();
    LocalDateTime getLastInteractedAt();
}
