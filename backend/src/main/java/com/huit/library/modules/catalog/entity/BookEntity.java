package com.huit.library.modules.catalog.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "books")
public class BookEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookId;

    private String title;
    private String isbn;
    private String ddcCallNumber;
    private Integer publishYear;
    private String language;
    private String labelColor;
    private BigDecimal defaultPrice;
    private String priceCurrency = "VND";
    private String priceSource;
    private LocalDateTime priceUpdatedAt;

    @Column(nullable = false)
    private String priceStatus = "UNKNOWN";

    @Column(nullable = false)
    private String materialType = "BOOK";

    private Boolean isDigital = false;

    private String author;
    private String publisher;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long viewCount = 0L;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    @Column(name = "image_url_s")
    private String imageUrlS;
    
    @Column(name = "image_url_m")
    private String imageUrlM;
    
    @Column(name = "image_url_l")
    private String imageUrlL;

    @PrePersist
    public void prePersist() {
        if (priceCurrency == null || priceCurrency.isBlank()) {
            priceCurrency = "VND";
        }
        if (materialType == null || materialType.isBlank()) {
            materialType = "BOOK";
        }
        if (priceStatus == null || priceStatus.isBlank()) {
            if (defaultPrice != null && defaultPrice.compareTo(BigDecimal.ZERO) > 0) {
                priceStatus = "KNOWN";
                if (priceSource == null || priceSource.isBlank()) {
                    priceSource = "CATALOG";
                }
                if (priceUpdatedAt == null) {
                    priceUpdatedAt = LocalDateTime.now();
                }
            } else {
                priceStatus = "UNKNOWN";
            }
        }
        if (viewCount == null) {
            viewCount = 0L;
        }
        if (isDigital == null) {
            isDigital = false;
        }
    }
}
