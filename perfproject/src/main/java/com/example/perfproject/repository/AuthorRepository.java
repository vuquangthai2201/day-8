package com.example.perfproject.repository;

import com.example.perfproject.model.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // TRƯỚC (gây N+1): findAll() rồi duyệt author.getBooks() -> 1 + N query.
    // SAU: JOIN FETCH nạp author + books trong đúng 1 query.
    @Query("SELECT DISTINCT a FROM Author a LEFT JOIN FETCH a.books")
    List<Author> findAllWithBooks();
}
