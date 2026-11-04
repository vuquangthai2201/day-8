package com.example.library.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loans")
public class Loan {
    @Id
    private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Book book;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Member member;
    @Column(nullable = false)
    private LocalDate borrowedAt;
    @Column(nullable = false)
    private LocalDate dueAt;
    private LocalDate returnedAt;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal penalty = BigDecimal.ZERO;

    protected Loan() {}

    public Loan(Book book, Member member, LocalDate borrowedAt, LocalDate dueAt) {
        this.id = UUID.randomUUID();
        this.book = book;
        this.member = member;
        this.borrowedAt = borrowedAt;
        this.dueAt = dueAt;
        this.penalty = BigDecimal.ZERO;
    }

    public void completeReturn(LocalDate returnedAt, BigDecimal penalty) {
        if (this.returnedAt != null) throw new IllegalStateException("Loan has already been returned");
        this.returnedAt = returnedAt;
        this.penalty = penalty;
    }

    public UUID getId() { return id; }
    public Book getBook() { return book; }
    public Member getMember() { return member; }
    public LocalDate getBorrowedAt() { return borrowedAt; }
    public LocalDate getDueAt() { return dueAt; }
    public LocalDate getReturnedAt() { return returnedAt; }
    public BigDecimal getPenalty() { return penalty; }
}
