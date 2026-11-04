package com.example.library.application;

import com.example.library.application.penalty.PenaltyPolicy;
import com.example.library.application.port.*;
import com.example.library.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LibraryService {
    private static final int LOAN_PERIOD_DAYS = 14;

    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final List<PenaltyPolicy> penaltyPolicies;

    public LibraryService(BookRepository books, MemberRepository members,
                          LoanRepository loans, List<PenaltyPolicy> penaltyPolicies) {
        this.books = books;
        this.members = members;
        this.loans = loans;
        this.penaltyPolicies = penaltyPolicies;
    }

    @Transactional
    public Member addMember(String fullName, String email) {
        return members.save(new Member(fullName, email));
    }

    @Transactional
    public Book addBook(String title, String author, String isbn) {
        if (books.existsByIsbn(isbn)) {
            throw new IllegalArgumentException("ISBN already exists: " + isbn);
        }
        return books.save(new Book(title, author, isbn));
    }

    @Transactional
    public Loan borrowBook(UUID memberId, UUID bookId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        Book book = books.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
        book.markBorrowed();
        books.save(book);
        LocalDate today = LocalDate.now();
        return loans.save(new Loan(book, member, today, today.plusDays(LOAN_PERIOD_DAYS)));
    }

    @Transactional
    public Loan returnBook(UUID loanId, String penaltyCode) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
        PenaltyPolicy policy = penaltyPolicies.stream()
                .filter(candidate -> candidate.code().equalsIgnoreCase(penaltyCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported penalty policy: " + penaltyCode));

        LocalDate returnedAt = LocalDate.now();
        loan.completeReturn(returnedAt, policy.calculate(loan.getDueAt(), returnedAt));
        loan.getBook().markReturned();
        books.save(loan.getBook());
        return loans.save(loan);
    }
}
