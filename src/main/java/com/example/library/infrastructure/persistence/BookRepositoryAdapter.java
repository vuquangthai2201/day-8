package com.example.library.infrastructure.persistence;

import com.example.library.application.port.BookRepository;
import com.example.library.domain.Book;
import com.example.library.infrastructure.persistence.springdata.SpringDataBookRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookRepositoryAdapter implements BookRepository {
    private final SpringDataBookRepository repository;
    public BookRepositoryAdapter(SpringDataBookRepository repository) { this.repository = repository; }
    @Override public Book save(Book book) { return repository.save(book); }
    @Override public Optional<Book> findById(UUID id) { return repository.findById(id); }
    @Override public boolean existsByIsbn(String isbn) { return repository.existsByIsbn(isbn); }
}
