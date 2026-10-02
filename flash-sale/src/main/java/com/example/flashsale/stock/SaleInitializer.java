package com.example.flashsale.stock;

import com.example.flashsale.catalog.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Nạp tồn kho từ Postgres vào Redis khi khởi động (không ghi đè nếu đã có). */
@Component
public class SaleInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SaleInitializer.class);
    private final ProductRepository products;
    private final HoldStore holds;

    public SaleInitializer(ProductRepository products, HoldStore holds) {
        this.products = products;
        this.holds = holds;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (long id : products.allIds()) {
            int stock = products.stock(id).orElse(0);
            try {
                holds.init(id, stock);
                log.info("Khởi tạo cổng cho sản phẩm {} với tồn kho {}", id, stock);
            } catch (RuntimeException e) {
                log.warn("Không khởi tạo được cổng Redis cho sản phẩm {}: {}", id, e.toString());
            }
        }
    }
}
