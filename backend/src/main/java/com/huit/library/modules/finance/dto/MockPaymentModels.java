package com.huit.library.modules.finance.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class MockPaymentModels {
    private MockPaymentModels() { }
    public enum Purpose { DEBT, DEPOSIT }
    public enum Outcome { SUCCESS, CANCEL, FAIL }
    public record Create(@NotNull Purpose purpose,
                         @NotNull @DecimalMin("1000") @DecimalMax("100000000") @Digits(integer = 9, fraction = 0) BigDecimal amount,
                         @NotNull UUID idempotencyKey) { }
    public record Finish(@NotNull Outcome outcome) { }
}
