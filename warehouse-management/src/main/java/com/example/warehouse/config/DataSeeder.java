package com.example.warehouse.config;

import com.example.warehouse.entity.Inventory;
import com.example.warehouse.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
    CommandLineRunner seed(InventoryRepository repository) {
        return args -> {
            if (!repository.existsById(1L)) {
                repository.save(new Inventory(1L, "Laptop Dell XPS", 10));
            }
        };
    }
}
