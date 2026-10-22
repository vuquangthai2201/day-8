package com.example.perfproject.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "book", indexes = {
        // Index cho câu query lọc theo năm xuất bản (tránh full table scan)
        @Index(name = "idx_book_publication_year", columnList = "publicationYear"),
        // Index cho khoá ngoại author_id (PostgreSQL không tự tạo; MySQL/InnoDB thì có)
        @Index(name = "idx_book_author_id", columnList = "author_id")
})
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;

    private int publicationYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Book(id=" + id + ", title=" + title + ", publicationYear=" + publicationYear + ")";
    }
}
