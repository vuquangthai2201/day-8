package com.example.library.application.penalty;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface PenaltyPolicy {
    String code();
    BigDecimal calculate(LocalDate dueDate, LocalDate returnedDate);
}
