package com.example.flashsale.payment;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final ConcurrentHashMap<String, PaymentResult> processed = new ConcurrentHashMap<>();
    private final double failureRate;
    private final long latencyMs;

    public SimulatedPaymentGateway(@Value("${app.payment.failure-rate:0.0}") double failureRate,
                                   @Value("${app.payment.latency-ms:0}") long latencyMs) {
        this.failureRate = failureRate;
        this.latencyMs = latencyMs;
    }

    @Override
    public PaymentResult charge(String idempotencyKey, BigDecimal amount) {
        return processed.computeIfAbsent(idempotencyKey, k -> {
            sleep();
            boolean fail = Math.floorMod(k.hashCode(), 100) < failureRate * 100;
            return new PaymentResult(!fail, "SIM-" + k);
        });
    }

    private void sleep() {
        if (latencyMs <= 0) return;
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
