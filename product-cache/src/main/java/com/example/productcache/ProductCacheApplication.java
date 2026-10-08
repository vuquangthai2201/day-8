package com.example.productcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ProductCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductCacheApplication.class, args);
    }
}
