package com.example.productcache.service;

import com.example.productcache.dto.ProductRequest;
import com.example.productcache.dto.ProductResponse;
import com.example.productcache.entity.Product;
import com.example.productcache.exception.ProductNotFoundException;
import com.example.productcache.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Tầng DB + L2 cache (Redis) thông qua annotation của Spring Cache.
 *
 * Lưu ý: cố ý KHÔNG dùng @Transactional ở đây. Mỗi lệnh của repository (save, deleteById, ...)
 * đã tự chạy trong transaction riêng và commit xong trước khi method trả về. Nhờ vậy
 * @CachePut/@CacheEvict (chạy SAU khi method thành công) luôn diễn ra sau khi DB đã commit,
 * không phụ thuộc vào thứ tự giữa advice transaction và advice cache.
 */
@Service
public class ProductService {

    public static final String CACHE_NAME = "products";

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public ProductResponse createProduct(ProductRequest request) {
        Product saved = repository.save(new Product(request.name(), request.description(), request.price()));
        return ProductResponse.from(saved);
    }

    /** Thân method chỉ chạy khi L2 MISS -> log bên dưới cho biết có truy cập DB. */
    @Cacheable(cacheNames = CACHE_NAME, key = "#id")
    public ProductResponse getProduct(Long id) {
        log.info("[DB   ] L2 miss -> loading product {} from database", id);
        return repository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /** Cập nhật DB rồi ghi đè giá trị mới vào L2. */
    @CachePut(cacheNames = CACHE_NAME, key = "#id")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        return ProductResponse.from(repository.save(product));
    }

    /** Xóa khỏi DB, sau đó @CacheEvict xóa key tương ứng khỏi L2 (Redis). */
    @CacheEvict(cacheNames = CACHE_NAME, key = "#id")
    public void deleteProduct(Long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
        log.info("[DB   ] product {} deleted from database", id);
    }
}
