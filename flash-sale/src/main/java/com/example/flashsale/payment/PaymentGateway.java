package com.example.flashsale.payment;

import java.math.BigDecimal;

/** Cổng thanh toán. Cùng idempotencyKey phải luôn cho cùng kết quả và không bao giờ trừ tiền hai lần. */
public interface PaymentGateway {
    PaymentResult charge(String idempotencyKey, BigDecimal amount);
}
