package com.example.library.infrastructure.persistence;

import com.example.library.application.port.LoanRepository;
import com.example.library.domain.Loan;
import com.example.library.infrastructure.persistence.springdata.SpringDataLoanRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanRepositoryAdapter implements LoanRepository {
    private final SpringDataLoanRepository repository;
    public LoanRepositoryAdapter(SpringDataLoanRepository repository) { this.repository = repository; }
    @Override public Loan save(Loan loan) { return repository.save(loan); }
    @Override public Optional<Loan> findById(UUID id) { return repository.findById(id); }
}
