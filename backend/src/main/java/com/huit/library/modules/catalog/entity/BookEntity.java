package com.huit.library.modules.catalog.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

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
    private Boolean isDigital;

    private String author;
    private String publisher;
    
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
