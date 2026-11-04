package com.huit.library.modules.circulation.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface EvaluationInteractionProjection {
    Long getDetailId();

    UUID getUserId();

    Long getBookId();

    LocalDateTime getInteractionAt();

    Integer getRenewalCount();
}
