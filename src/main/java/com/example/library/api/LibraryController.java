package com.example.library.api;

import com.example.library.api.dto.*;
import com.example.library.application.LibraryService;
import com.example.library.domain.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LibraryController {
    private final LibraryService library;
    public LibraryController(LibraryService library) { this.library = library; }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@Valid @RequestBody CreateMemberRequest request) {
        Member member = library.addMember(request.fullName(), request.email());
        return new MemberResponse(member.getId(), member.getFullName(), member.getEmail());
    }

    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse addBook(@Valid @RequestBody CreateBookRequest request) {
        Book book = library.addBook(request.title(), request.author(), request.isbn());
        return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(), book.isAvailable());
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse borrow(@Valid @RequestBody BorrowBookRequest request) {
        return toResponse(library.borrowBook(request.memberId(), request.bookId()));
    }

    @PostMapping("/loans/{loanId}/return")
    public LoanResponse returnBook(@PathVariable UUID loanId, @Valid @RequestBody ReturnBookRequest request) {
        return toResponse(library.returnBook(loanId, request.penaltyPolicy()));
    }

    private LoanResponse toResponse(Loan loan) {
        return new LoanResponse(loan.getId(), loan.getBook().getId(), loan.getMember().getId(),
                loan.getBorrowedAt(), loan.getDueAt(), loan.getReturnedAt(), loan.getPenalty());
    }

    public record MemberResponse(UUID id, String fullName, String email) {}
    public record BookResponse(UUID id, String title, String author, String isbn, boolean available) {}
    public record LoanResponse(UUID id, UUID bookId, UUID memberId, LocalDate borrowedAt,
                               LocalDate dueAt, LocalDate returnedAt, BigDecimal penalty) {}
}
