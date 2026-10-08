package com.example.productcache.facade;

import com.example.productcache.cache.LocalProductCache;
import com.example.productcache.dto.ProductRequest;
import com.example.productcache.dto.ProductResponse;
import com.example.productcache.pubsub.ProductCacheEventPublisher;
import com.example.productcache.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Điều phối cache hai tầng:
 *
 *   READ  : L1 (Caffeine) -> L2 (Redis, qua ProductService @Cacheable) -> DB
 *   WRITE : ProductService (DB + L2) -> xóa L1 cục bộ -> publish Pub/Sub cho instance khác
 *
 * Bước publish PHẢI chạy sau khi ProductService trả về (lúc đó proxy đã commit DB và
 * xử lý xong @CacheEvict/@CachePut cho L2). Nếu publish sớm hơn, instance khác có thể nhận
 * message, đọc lại L2 còn dữ liệu cũ rồi nạp ngược vào L1 của nó.
 */
@Service
public class ProductFacade {

    private static final Logger log = LoggerFactory.getLogger(ProductFacade.class);

    private final ProductService productService;
    private final LocalProductCache localCache;
    private final ProductCacheEventPublisher publisher;

    public ProductFacade(ProductService productService,
                         LocalProductCache localCache,
                         ProductCacheEventPublisher publisher) {
        this.productService = productService;
        this.localCache = localCache;
        this.publisher = publisher;
    }

    public ProductResponse getProduct(Long id) {
        ProductResponse cached = localCache.get(id);
        if (cached != null) {
            log.info("[L1   ] HIT  product {}", id);
            return cached;
        }
        log.info("[L1   ] MISS product {}", id);
        ProductResponse product = productService.getProduct(id);   // L2 -> DB
        localCache.put(id, product);
        return product;
    }

    public ProductResponse createProduct(ProductRequest request) {
        return productService.createProduct(request);
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {
        ProductResponse updated = productService.updateProduct(id, request);   // DB + L2 (@CachePut)
        localCache.evict(id);                                                   // L1 cục bộ
        publisher.publishUpdate(id);                                            // L1 của instance khác
        return updated;
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);        // DB + L2 (@CacheEvict)
        localCache.evict(id);                    // L1 cục bộ
        publisher.publishDeletion(id);           // L1 của instance khác (channel product-deletion)
    }
}
