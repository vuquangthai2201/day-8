package com.example.library.application.port;

import com.example.library.domain.Loan;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository {
    Loan save(Loan loan);
    Optional<Loan> findById(UUID id);
}
