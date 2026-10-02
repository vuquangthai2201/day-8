package com.example.flashsale.checkout;

import com.example.flashsale.catalog.ProductRepository;
import com.example.flashsale.error.*;
import com.example.flashsale.payment.PaymentGateway;
import com.example.flashsale.payment.PaymentResult;
import com.example.flashsale.stock.HoldStore;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checkout (PC/EC). Hai pha:
 *  1) TX ngắn trên Postgres primary: tạo order PENDING + trừ kho có điều kiện (chốt chặn oversell).
 *  2) Thanh toán idempotent ngoài TX (không giữ khóa dòng khi gọi mạng), rồi chuyển trạng thái có điều kiện.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ProductRepository products;
    private final OrderRepository orders;
    private final HoldStore holds;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;
    private final Duration checkoutWindow;
    private final boolean sync;
    private final ExecutorService async = Executors.newVirtualThreadPerTaskExecutor();

    public CheckoutService(ProductRepository products, OrderRepository orders, HoldStore holds,
                           PaymentGateway gateway, TransactionTemplate tx,
                           @Value("${app.hold.checkout-window-seconds:300}") long windowSeconds,
                           @Value("${app.payment.sync:false}") boolean sync) {
        this.products = products;
        this.orders = orders;
        this.holds = holds;
        this.gateway = gateway;
        this.tx = tx;
        this.checkoutWindow = Duration.ofSeconds(windowSeconds);
        this.sync = sync;
    }

    @PreDestroy
    void shutdown() {
        async.shutdown();
    }

    public Order checkout(String userId, long productId, String idempotencyKey) {
        // Retry của cùng một request: trả lại đúng order cũ.
        var existing = orders.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        long qty = holds.beginCheckout(productId, userId, Instant.now().plus(checkoutWindow));
        if (qty < 0) throw new NoActiveHoldException();

        Order order;
        try {
            order = tx.execute(status -> {
                BigDecimal price = products.price(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId));
                Order o = new Order(UUID.randomUUID(), userId, productId, (int) qty,
                        price.multiply(BigDecimal.valueOf(qty)), "PENDING", idempotencyKey);
                orders.insertPending(o);                      // kiểm tra unique sớm
                if (products.tryDecrement(productId, (int) qty) == 0) {
                    throw new SoldOutException();             // rollback cả insert
                }
                return o;                                     // khóa dòng product chỉ giữ tới commit
            });
        } catch (DuplicateKeyException e) {
            var again = orders.findByIdempotencyKey(idempotencyKey);
            if (again.isPresent()) return again.get();        // hai request song song cùng key
            holds.finish(productId, userId, true);            // vi phạm (user, product): đã mua rồi
            throw new AlreadyPurchasedException();
        } catch (SoldOutException e) {
            holds.finish(productId, userId, false);           // DB đã hết thật, không trả về cổng
            throw e;
        }

        if (sync) {
            settle(order);
            return orders.findById(order.id()).orElse(order);
        }
        async.execute(() -> settle(order));
        return order;
    }

    /** Dùng chung cho luồng chính và reconciler. An toàn khi chạy lặp hoặc song song. */
    public void settle(Order order) {
        PaymentResult result;
        try {
            result = gateway.charge(order.id().toString(), order.amount()); // key = orderId
        } catch (RuntimeException e) {
            log.warn("Chưa rõ kết quả thanh toán order {}, giữ PENDING cho reconciler: {}", order.id(), e.toString());
            return; // KHÔNG đánh FAILED khi không chắc
        }

        String target = result.success() ? "PAID" : "FAILED";
        Boolean changed = tx.execute(status -> {
            int n = orders.markIfPending(order.id(), target);
            if (n == 1 && !result.success()) {
                products.increment(order.productId(), order.qty()); // hoàn kho đúng một lần
            }
            return n == 1;
        });

        if (Boolean.TRUE.equals(changed)) {
            try {
                holds.finish(order.productId(), order.userId(), !result.success());
            } catch (RuntimeException e) {
                log.warn("Không kết thúc được hold (Redis), reaper sẽ dọn: {}", e.toString());
            }
        }
    }
}
