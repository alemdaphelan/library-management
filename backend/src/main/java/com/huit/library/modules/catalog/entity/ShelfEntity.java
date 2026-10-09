package com.huit.library.modules.catalog.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "shelves")
public class ShelfEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shelfId;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookshelf_id")
    private BookshelfEntity bookshelf;
}
