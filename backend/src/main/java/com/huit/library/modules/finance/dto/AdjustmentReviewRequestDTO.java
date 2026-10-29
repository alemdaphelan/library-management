package com.huit.library.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdjustmentReviewRequestDTO {
    @NotBlank
    private String action;
    @Size(max = 1000)
    private String note;
}
