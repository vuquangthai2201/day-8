package com.example.flashsale.checkout;

import java.math.BigDecimal;
import java.util.UUID;

public record Order(UUID id, String userId, long productId, int qty, BigDecimal amount,
                    String status, String idempotencyKey) {}
