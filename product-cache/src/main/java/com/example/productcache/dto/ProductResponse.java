package com.example.productcache.dto;

import com.example.productcache.entity.Product;

import java.math.BigDecimal;

/**
 * Đối tượng này được lưu trong cả L1 (Caffeine) lẫn L2 (Redis, dạng JSON),
 * nên cache KHÔNG bao giờ giữ JPA entity.
 */
public record ProductResponse(Long id, String name, String description, BigDecimal price) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice());
    }
}
