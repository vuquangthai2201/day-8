package com.example.productcache.cache;

import com.example.productcache.dto.ProductResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * L1 cache: nằm trong bộ nhớ JVM của MỖI instance (rất nhanh, nhưng không chia sẻ giữa các instance).
 * Vì vậy khi dữ liệu thay đổi/xóa, ta phải báo cho các instance khác qua Redis Pub/Sub.
 * TTL ngắn đóng vai trò "lưới an toàn" phòng khi một instance lỡ mất message Pub/Sub.
 */
@Component
public class LocalProductCache {

    private final Cache<Long, ProductResponse> cache;

    public LocalProductCache(@Value("${app.cache.l1.ttl-seconds:60}") long ttlSeconds,
                             @Value("${app.cache.l1.max-size:1000}") long maxSize) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
    }

    public ProductResponse get(Long id) {
        return cache.getIfPresent(id);
    }

    public void put(Long id, ProductResponse product) {
        cache.put(id, product);
    }

    public void evict(Long id) {
        cache.invalidate(id);
    }
}
