package com.example.library.application.penalty;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class DailyPenaltyPolicy implements PenaltyPolicy {
    private static final BigDecimal FEE_PER_DAY = new BigDecimal("5000.00");

    @Override public String code() { return "DAILY"; }

    @Override
    public BigDecimal calculate(LocalDate dueDate, LocalDate returnedDate) {
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, returnedDate));
        return FEE_PER_DAY.multiply(BigDecimal.valueOf(overdueDays));
    }
}
