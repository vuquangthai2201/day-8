package com.example.flashsale.admin;

import com.example.flashsale.catalog.ProductRepository;
import com.example.flashsale.error.ProductNotFoundException;
import com.example.flashsale.stock.HoldStore;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Chỉ để demo/test. Production cần xác thực và phân quyền. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    public record StockRequest(int stock) {}

    private final ProductRepository products;
    private final HoldStore holds;

    public AdminController(ProductRepository products, HoldStore holds) {
        this.products = products;
        this.holds = holds;
    }

    @PostMapping("/products/{id}/stock")
    public Map<String, Object> setStock(@PathVariable long id, @RequestBody StockRequest body) {
        if (body.stock() < 0) throw new IllegalArgumentException("stock phải >= 0");
        if (products.setStock(id, body.stock()) == 0) throw new ProductNotFoundException(id);
        holds.reset(id, body.stock());
        return Map.of("productId", id, "stock", body.stock());
    }
}
