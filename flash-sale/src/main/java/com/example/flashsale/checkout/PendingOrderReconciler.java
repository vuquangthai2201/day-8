package com.example.flashsale.checkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Xử lý đơn PENDING bị treo (app crash giữa chừng, cổng thanh toán timeout).
 * Gọi lại cổng với cùng idempotency key nên không thể trừ tiền hai lần.
 */
@Component
public class PendingOrderReconciler {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderReconciler.class);

    private final OrderRepository orders;
    private final CheckoutService checkout;
    private final int staleAfterSeconds;

    public PendingOrderReconciler(OrderRepository orders, CheckoutService checkout,
                                  @Value("${app.reconcile.stale-after-seconds:30}") int staleAfterSeconds) {
        this.orders = orders;
        this.checkout = checkout;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    @Scheduled(fixedDelayString = "${app.reconcile.interval-ms:10000}")
    public void run() {
        try {
            for (Order o : orders.findStalePending(staleAfterSeconds, 100)) {
                log.info("Reconcile order {}", o.id());
                checkout.settle(o);
            }
        } catch (RuntimeException e) {
            log.warn("Reconciler lỗi, thử lại ở lượt sau: {}", e.toString());
        }
    }
}
