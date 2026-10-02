package com.example.flashsale.stock;

import com.example.flashsale.catalog.ProductRepository;
import com.example.flashsale.error.ProductNotFoundException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Luồng listing (PA/EL): cache cục bộ ngắn + single-flight để không bị thundering herd.
 * Nguồn: Redis counter (gần real-time); nếu Redis lỗi thì rơi về replica (trễ nhưng vẫn phục vụ được).
 * Không bao giờ dùng kết quả này để quyết định bán.
 */
@Service
public class StockViewService {

    private static final Logger log = LoggerFactory.getLogger(StockViewService.class);

    private record Snapshot(long value, String source, long loadedAt) {}

    private static final class Entry {
        final ReentrantLock lock = new ReentrantLock();
        volatile Snapshot snap = new Snapshot(0, "none", 0);
    }

    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();
    private final HoldStore holds;
    private final ProductRepository products;
    private final long ttlMs;

    public StockViewService(HoldStore holds, ProductRepository products,
                            @Value("${app.listing.cache-ms:500}") long ttlMs) {
        this.holds = holds;
        this.products = products;
        this.ttlMs = ttlMs;
    }

    public StockView get(long productId) {
        Entry e = cache.computeIfAbsent(productId, k -> new Entry());
        Snapshot s = e.snap;
        if (!fresh(s)) {
            e.lock.lock(); // single-flight: một request đi nạp, số còn lại chờ rồi dùng kết quả mới
            try {
                s = e.snap;
                if (!fresh(s)) {
                    s = load(productId);
                    e.snap = s;
                }
            } finally {
                e.lock.unlock();
            }
        }
        return new StockView(productId, s.value(), Instant.ofEpochMilli(s.loadedAt()), s.source());
    }

    private boolean fresh(Snapshot s) {
        return System.currentTimeMillis() - s.loadedAt() < ttlMs;
    }

    private Snapshot load(long productId) {
        try {
            Long v = holds.available(productId);
            if (v != null) return new Snapshot(v, "redis", System.currentTimeMillis());
        } catch (RuntimeException ex) {
            log.warn("Redis lỗi khi đọc listing, rơi về replica: {}", ex.toString());
        }
        int v = products.stockFromReplica(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        return new Snapshot(v, "replica", System.currentTimeMillis());
    }
}
