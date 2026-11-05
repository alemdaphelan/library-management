package com.huit.library.modules.circulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoanLossReportRequestDTO(
        @NotBlank @Size(max = 1000) String description) { }
