package com.example.flashsale.cart;

import com.example.flashsale.error.ProductNotFoundException;
import com.example.flashsale.error.SoldOutException;
import com.example.flashsale.stock.HoldStore;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Add-to-cart (PC/EL): giữ hàng mềm trong Redis bằng Lua nguyên tử.
 * Redis lỗi => ném lỗi (503), không bán mù.
 */
@Service
public class CartService {

    public record HoldResult(boolean alreadyHeld, int qty, Instant expiresAt) {}

    private final HoldStore holds;
    private final Duration ttl;
    private final int maxQty;

    public CartService(HoldStore holds,
                       @Value("${app.hold.ttl-seconds:120}") long ttlSeconds,
                       @Value("${app.hold.max-qty-per-user:2}") int maxQty) {
        this.holds = holds;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.maxQty = maxQty;
    }

    public HoldResult add(String userId, long productId, int qty) {
        if (qty < 1 || qty > maxQty) {
            throw new IllegalArgumentException("Số lượng phải từ 1 đến " + maxQty);
        }
        Instant expiresAt = Instant.now().plus(ttl);
        long r = holds.reserve(productId, userId, qty, expiresAt);
        if (r == -1) throw new SoldOutException();
        if (r == -3) throw new ProductNotFoundException(productId);
        if (r == -2) {
            Integer held = holds.heldQty(productId, userId);
            return new HoldResult(true, held == null ? 0 : held, null);
        }
        return new HoldResult(false, (int) r, expiresAt);
    }
}
