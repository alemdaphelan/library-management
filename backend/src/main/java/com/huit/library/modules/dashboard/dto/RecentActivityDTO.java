package com.huit.library.modules.dashboard.dto;

import java.time.LocalDateTime;

public record RecentActivityDTO(
        String type,
        String user,
        String action,
        String item,
        LocalDateTime time) {
}
