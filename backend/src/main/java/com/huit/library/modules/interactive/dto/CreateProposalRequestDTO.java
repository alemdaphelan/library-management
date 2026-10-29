package com.huit.library.modules.interactive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProposalRequestDTO {
    @NotBlank(message = "Tên sách đề xuất không được để trống")
    @Size(max = 255, message = "Tên sách đề xuất không được vượt quá 255 ký tự")
    private String title;

    @Size(max = 255, message = "Tên tác giả không được vượt quá 255 ký tự")
    private String authorName;

    @Size(max = 255, message = "Nhà xuất bản không được vượt quá 255 ký tự")
    private String publisher;

    @Size(max = 32, message = "ISBN không được vượt quá 32 ký tự")
    private String isbn;

    @Min(value = 1000, message = "Năm xuất bản không hợp lệ")
    @Max(value = 2100, message = "Năm xuất bản không hợp lệ")
    private Integer publicationYear;

    @Size(max = 100, message = "Phiên bản không được vượt quá 100 ký tự")
    private String edition;

    @DecimalMin(value = "0", inclusive = true, message = "Giá dự kiến không được âm")
    private BigDecimal estimatedPrice;

    @Size(max = 1000, message = "Đường dẫn tham khảo không được vượt quá 1000 ký tự")
    private String externalUrl;

    @Size(max = 255, message = "Thể loại không được vượt quá 255 ký tự")
    private String genre;

    @Pattern(regexp = "LOW|NORMAL|HIGH", message = "Mức độ ưu tiên phải là LOW, NORMAL hoặc HIGH")
    private String urgencyLevel = "NORMAL";

    @NotBlank(message = "Lý do đề xuất không được để trống")
    @Size(max = 1000, message = "Lý do đề xuất không được vượt quá 1000 ký tự")
    private String reason;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;
}
