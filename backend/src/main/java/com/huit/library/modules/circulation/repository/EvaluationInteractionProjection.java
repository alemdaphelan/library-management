package com.huit.library.modules.circulation.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface EvaluationInteractionProjection {
    UUID getUserId();

    Long getBookId();

    LocalDateTime getInteractionAt();
}
