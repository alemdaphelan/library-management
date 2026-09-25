package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<BookEntity> getAllBooks() {
        return bookRepository.findAll();
    }

    public List<BookEntity> searchBooks(String query) {
        if (query == null || query.isBlank()) {
            return getAllBooks();
        }
        // Basic filter on title or isbn for now (in-memory or JPA derived queries could be used)
        return bookRepository.findAll().stream()
                .filter(b -> (b.getTitle() != null && b.getTitle().toLowerCase().contains(query.toLowerCase())) ||
                             (b.getIsbn() != null && b.getIsbn().contains(query)))
                .collect(Collectors.toList());
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
