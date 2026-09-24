package com.huit.library.modules.interactive.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Document(collection = "user_events")
public class UserEventDocument {
    @Id
    private String logId;

    private UUID userId;
    private String eventType; // VIEW, CLICK, LOAN
    private String itemId;
    private LocalDateTime timestamp;
}
