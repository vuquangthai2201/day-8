package com.example.library.application.port;

import com.example.library.domain.Book;
import java.util.Optional;
import java.util.UUID;

public interface BookRepository {
    Book save(Book book);
    Optional<Book> findById(UUID id);
    boolean existsByIsbn(String isbn);
}
