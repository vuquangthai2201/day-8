package com.example.flashsale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.flashsale.cart.CartService;
import com.example.flashsale.catalog.ProductRepository;
import com.example.flashsale.checkout.CheckoutService;
import com.example.flashsale.checkout.OrderRepository;
import com.example.flashsale.stock.HoldStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.payment.sync=true",
        "app.payment.latency-ms=0",
        "app.payment.failure-rate=0.0"
})
@Testcontainers(disabledWithoutDocker = true)
class OversellIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired HoldStore holds;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanOrders() {
        jdbc.update("delete from orders");
    }

    private void stampede(int users) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch done = new CountDownLatch(users);
        for (int i = 0; i < users; i++) {
            final String user = "user-" + i;
            pool.submit(() -> {
                try {
                    cart.add(user, 1, 1);
                    checkout.checkout(user, 1, "key-" + user);
                } catch (RuntimeException ignored) {
                    // hết hàng / không có hold: bình thường trong stampede
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    @Test
    void redisGateNeverAdmitsMoreThanStock() throws Exception {
        products.setStock(1, 50);
        holds.reset(1, 50);

        stampede(500);

        assertEquals(50, orders.countByStatus("PAID"));
        assertEquals(0, products.stock(1).orElseThrow());
    }

    @Test
    void databaseStillPreventsOversellEvenIfRedisOvercounts() throws Exception {
        // Mô phỏng Redis failover làm mất/đếm sai: cổng cho phép 1000, DB chỉ có 5.
        products.setStock(1, 5);
        holds.reset(1, 1000);

        stampede(300);

        assertEquals(5, orders.countByStatus("PAID"));
        assertEquals(0, products.stock(1).orElseThrow());
    }
}
