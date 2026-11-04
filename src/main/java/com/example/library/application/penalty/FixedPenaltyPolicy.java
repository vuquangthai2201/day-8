package com.example.library.application.penalty;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class FixedPenaltyPolicy implements PenaltyPolicy {
    private static final BigDecimal FIXED_FEE = new BigDecimal("30000.00");

    @Override public String code() { return "FIXED"; }

    @Override
    public BigDecimal calculate(LocalDate dueDate, LocalDate returnedDate) {
        return returnedDate.isAfter(dueDate) ? FIXED_FEE : BigDecimal.ZERO;
    }
}
