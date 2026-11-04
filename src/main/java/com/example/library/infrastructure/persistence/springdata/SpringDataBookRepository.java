package com.example.library.infrastructure.persistence.springdata;

import com.example.library.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpringDataBookRepository extends JpaRepository<Book, UUID> {
    boolean existsByIsbn(String isbn);
}
