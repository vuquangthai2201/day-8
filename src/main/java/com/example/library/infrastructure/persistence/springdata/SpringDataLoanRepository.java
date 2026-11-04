package com.example.library.infrastructure.persistence.springdata;

import com.example.library.domain.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpringDataLoanRepository extends JpaRepository<Loan, UUID> {}
