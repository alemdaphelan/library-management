package com.huit.library.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CashDepositRequestDTO(
        @NotNull UUID userId,
        @NotNull @DecimalMin("1") @Digits(integer = 15, fraction = 0) BigDecimal amount,
        @NotNull UUID idempotencyKey,
        @Size(max = 200) String note) {}
