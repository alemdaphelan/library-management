package com.huit.library.modules.catalog.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationNodeDTO {
    private Long id;
    private String name;
    private String type;
    private Long parentId;
    private String parentName;
}
