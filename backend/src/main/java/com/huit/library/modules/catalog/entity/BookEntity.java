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
    private String priceCurrency;
    private String priceSource;
    private LocalDateTime priceUpdatedAt;
    private String priceStatus;
    private String materialType;
    private Boolean isDigital;

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
}
