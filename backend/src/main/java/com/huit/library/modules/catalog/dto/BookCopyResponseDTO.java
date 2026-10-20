package com.huit.library.modules.catalog.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookCopyResponseDTO {
    private String barcode;
    private Long bookId;
    private String conditionNote;
    private String status;
    private Long shelfId;
    private String shelfName;
    private Long bookshelfId;
    private String bookshelfName;
    private Long floorId;
    private String floorName;
    private String location;
}
