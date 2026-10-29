package com.huit.library.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ImportReceiptRequestDTO {
    @NotNull
    private Long supplierId;

    @Valid
    @NotEmpty
    private List<Line> lines;

    @Data
    public static class Line {
        private Long bookId;
        private String isbn;
        @NotNull
        @Min(1)
        private Long shelfId;
        private Long floorId;
        private Long bookshelfId;

        @NotNull
        @Min(1)
        private Integer quantity;

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal unitPrice;
    }
}
