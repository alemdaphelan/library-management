package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookCopyService {

    private final BookCopyRepository bookCopyRepository;

    public BookCopyService(BookCopyRepository bookCopyRepository) {
        this.bookCopyRepository = bookCopyRepository;
    }

    public List<BookCopyEntity> getCopiesByBookId(Long bookId) {
        // Here we just fetch all and filter for demo, better to add findByBookId in repository
        return bookCopyRepository.findAll().stream()
                .filter(copy -> bookId.equals(copy.getBookId()))
                .toList();
    }

    public BookCopyEntity updateLocation(String barcode, String newLocation) {
        BookCopyEntity copy = bookCopyRepository.findById(barcode)
                .orElseThrow(() -> new RuntimeException("Book copy not found"));
        copy.setLocation(newLocation);
        return bookCopyRepository.save(copy);
    }
    
    public BookCopyEntity updateStatus(String barcode, String newStatus) {
        BookCopyEntity copy = bookCopyRepository.findById(barcode)
                .orElseThrow(() -> new RuntimeException("Book copy not found"));
        copy.setStatus(newStatus);
        return bookCopyRepository.save(copy);
    }
}
