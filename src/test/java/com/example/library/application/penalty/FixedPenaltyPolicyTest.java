package com.example.library.application.penalty;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedPenaltyPolicyTest {
    private final FixedPenaltyPolicy policy = new FixedPenaltyPolicy();

    @Test
    void chargesOneFixedFeeWhenOverdue() {
        BigDecimal result = policy.calculate(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        assertEquals(new BigDecimal("30000.00"), result);
    }

    @Test
    void doesNotChargeWhenReturnedOnTime() {
        BigDecimal result = policy.calculate(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        assertEquals(BigDecimal.ZERO, result);
    }
}
