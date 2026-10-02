package com.example.flashsale.stock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockController {

    private final StockViewService service;

    public StockController(StockViewService service) {
        this.service = service;
    }

    @GetMapping("/products/{id}/stock")
    public StockView stock(@PathVariable long id) {
        return service.get(id);
    }
}
