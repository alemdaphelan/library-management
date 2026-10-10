package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Page<BookEntity> getAllBooks(Pageable pageable) {
        return bookRepository.findAll(pageable);
    }

    public Page<BookEntity> searchBooks(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return getAllBooks(pageable);
        }
        return bookRepository.findByTitleContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrAuthorContainingIgnoreCase(query, query, query, pageable);
    }

    public BookEntity getBookById(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new RuntimeException("Book not found"));
    }

    public BookEntity createBook(BookEntity book) {
        return bookRepository.save(book);
    }
    
    public BookEntity updateBook(Long id, BookEntity updatedBook) {
        BookEntity existing = getBookById(id);
        existing.setTitle(updatedBook.getTitle());
        existing.setIsbn(updatedBook.getIsbn());
        existing.setDdcCallNumber(updatedBook.getDdcCallNumber());
        existing.setPublishYear(updatedBook.getPublishYear());
        existing.setLanguage(updatedBook.getLanguage());
        existing.setLabelColor(updatedBook.getLabelColor());
        existing.setDefaultPrice(updatedBook.getDefaultPrice());
        existing.setIsDigital(updatedBook.getIsDigital());
        return bookRepository.save(existing);
    }

    public void deleteBook(Long id) {
        bookRepository.deleteById(id);
    }
}
