package com.huit.library.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LocationRequestDTO {
    @NotBlank
    private String name;
    private Long parentId;
    @jakarta.validation.constraints.Min(0)
    private Integer capacity;
}
