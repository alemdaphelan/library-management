package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BorrowEligibilityDTO {
    private int maximumItems;
    private long openItems;
    private long remainingSlots;
    private boolean eligible;
    private String message;
}
