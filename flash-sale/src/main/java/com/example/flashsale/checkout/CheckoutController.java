package com.example.flashsale.checkout;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutController {

    public record CheckoutRequest(long productId) {}

    private final CheckoutService service;
    private final OrderRepository orders;

    public CheckoutController(CheckoutService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(@RequestHeader("X-User-Id") String userId,
                                          @RequestHeader("Idempotency-Key") String key,
                                          @RequestBody CheckoutRequest body) {
        if (key.isBlank() || key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key không hợp lệ");
        }
        Order o = service.checkout(userId, body.productId(), key);
        // 202: đơn đã giữ hàng, thanh toán đang xử lý; 200: đã có kết quả cuối
        HttpStatus s = "PENDING".equals(o.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(s).body(o);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> order(@PathVariable UUID id) {
        return orders.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
