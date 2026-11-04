package com.example.library.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "books")
public class Book {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String author;
    @Column(nullable = false, unique = true)
    private String isbn;
    @Column(nullable = false)
    private boolean available = true;

    protected Book() {}

    public Book(String title, String author, String isbn) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.available = true;
    }

    public void markBorrowed() {
        if (!available) throw new IllegalStateException("Book is not available");
        available = false;
    }

    public void markReturned() {
        available = true;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public boolean isAvailable() { return available; }
}
