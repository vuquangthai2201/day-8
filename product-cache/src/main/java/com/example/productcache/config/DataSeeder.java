package com.example.productcache.config;

import com.example.productcache.entity.Product;
import com.example.productcache.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Nạp vài sản phẩm mẫu nếu bảng còn trống (chỉ phục vụ demo). */
@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository repository;

    public DataSeeder(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            repository.saveAll(List.of(
                    new Product("Laptop", "Laptop 14 inch", new BigDecimal("1200.00")),
                    new Product("Mouse", "Chuot khong day", new BigDecimal("25.50")),
                    new Product("Keyboard", "Ban phim co", new BigDecimal("80.00"))));
        }
    }
}
