package com.huit.library.modules.interactive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProposalStatusDTO {
    @NotBlank(message = "Trạng thái xử lý không được để trống")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Trạng thái chỉ có thể là APPROVED hoặc REJECTED")
    private String status;

    @Size(max = 1000, message = "Ghi chú xử lý không được vượt quá 1000 ký tự")
    private String adminNote;
}
