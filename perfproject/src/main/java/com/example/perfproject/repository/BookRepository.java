package com.example.perfproject.repository;

import com.example.perfproject.model.Book;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    // SAU: dùng index idx_book_publication_year; EntityGraph nạp luôn author
    // để không phát sinh N+1 khi đọc tên tác giả của từng sách.
    @EntityGraph(attributePaths = "author")
    List<Book> findByPublicationYear(int publicationYear);

    // TRƯỚC: không nạp author -> mỗi lần gọi book.getAuthor().getName() có thể phát sinh 1 query (N+1).
    @Query("SELECT b FROM Book b WHERE b.publicationYear = :year")
    List<Book> findByYearLazy(@Param("year") int year);

    // Dùng để đo riêng tác động của index (chỉ tốn chi phí tìm kiếm, không tốn chi phí map entity).
    long countByPublicationYear(int publicationYear);
}
