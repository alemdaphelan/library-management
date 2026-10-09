package com.huit.library.modules.catalog.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "bookshelves")
public class BookshelfEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookshelfId;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private FloorEntity floor;
}
