package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookCopyService {

    private final BookCopyRepository bookCopyRepository;
    private final ShelfRepository shelfRepository;

    public BookCopyService(BookCopyRepository bookCopyRepository, ShelfRepository shelfRepository) {
        this.bookCopyRepository = bookCopyRepository;
        this.shelfRepository = shelfRepository;
    }

    public List<BookCopyEntity> getCopiesByBookId(Long bookId) {
        // Here we just fetch all and filter for demo, better to add findByBookId in repository
        return bookCopyRepository.findAll().stream()
                .filter(copy -> bookId.equals(copy.getBookId()))
                .toList();
    }

    public BookCopyEntity updateLocation(String barcode, Long shelfId) {
        BookCopyEntity copy = bookCopyRepository.findById(barcode)
                .orElseThrow(() -> new RuntimeException("Book copy not found"));
        ShelfEntity shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> new RuntimeException("Shelf not found"));
        copy.setShelf(shelf);
        return bookCopyRepository.save(copy);
    }

    public BookCopyEntity updateStatus(String barcode, String newStatus) {
        BookCopyEntity copy = bookCopyRepository.findById(barcode)
                .orElseThrow(() -> new RuntimeException("Book copy not found"));
        copy.setStatus(newStatus);
        return bookCopyRepository.save(copy);
    }
}
