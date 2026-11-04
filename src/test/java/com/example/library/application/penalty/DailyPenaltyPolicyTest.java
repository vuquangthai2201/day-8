package com.example.library.application.penalty;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyPenaltyPolicyTest {
    private final DailyPenaltyPolicy policy = new DailyPenaltyPolicy();

    @Test
    void calculatesFeeForEachOverdueDay() {
        BigDecimal result = policy.calculate(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4));
        assertEquals(new BigDecimal("15000.00"), result);
    }

    @Test
    void doesNotChargeWhenReturnedOnTime() {
        BigDecimal result = policy.calculate(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        assertEquals(new BigDecimal("0.00"), result);
    }
}
