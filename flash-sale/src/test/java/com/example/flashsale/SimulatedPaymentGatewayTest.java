package com.example.flashsale;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.flashsale.payment.PaymentResult;
import com.example.flashsale.payment.SimulatedPaymentGateway;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTest {

    @Test
    void sameKeyReturnsSameResultWithoutChargingTwice() {
        var gw = new SimulatedPaymentGateway(0.5, 0);
        PaymentResult first = gw.charge("order-1", BigDecimal.TEN);
        PaymentResult second = gw.charge("order-1", BigDecimal.TEN);
        assertSame(first, second);
    }
}
