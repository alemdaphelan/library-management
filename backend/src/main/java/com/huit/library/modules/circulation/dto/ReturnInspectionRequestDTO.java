package com.huit.library.modules.circulation.dto;

import com.huit.library.modules.penalty.dto.PenaltyCaseRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReturnInspectionRequestDTO {
    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;

    @NotEmpty
    @Size(max = 3)
    private List<@NotNull @Valid Item> items;

    public enum Condition { NORMAL, DAMAGED, LOST }

    @Data
    public static class Item {
        @NotNull
        @Positive
        private Long loanDetailId;
        @NotNull
        private Condition condition;
        @Size(max = 500)
        private String returnConditionNote;
        @Valid
        private PenaltyCaseRequestDTO penalty;
    }
}
