package com.example.warehouse.controller;

import com.example.warehouse.dto.*;
import com.example.warehouse.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/inventories")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    /** Tao san pham moi trong kho (phuc vu test). */
    @PostMapping
    public ResponseEntity<InventoryResponse> create(@Valid @RequestBody CreateInventoryRequest req) {
        InventoryResponse body = InventoryResponse.from(service.create(req));
        return ResponseEntity.created(URI.create("/inventories/" + body.productId())).body(body);
    }

    /** Xem thong tin hien tai (lay version de dung cho PUT). */
    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable Long productId) {
        return InventoryResponse.from(service.get(productId));
    }

    /** Yeu cau 3: nhap hang - Pessimistic Locking. */
    @PostMapping("/{productId}/stock-in")
    public InventoryResponse stockIn(@PathVariable Long productId,
                                     @Valid @RequestBody StockInRequest req) {
        return InventoryResponse.from(service.stockIn(productId, req.quantity()));
    }

    /** Yeu cau 4: cap nhat thong tin - Optimistic Locking. */
    @PutMapping("/{productId}")
    public InventoryResponse update(@PathVariable Long productId,
                                    @Valid @RequestBody UpdateInventoryRequest req) {
        return InventoryResponse.from(service.update(productId, req));
    }

    /** Yeu cau 6: lich su thay doi (Envers). */
    @GetMapping("/{productId}/history")
    public List<InventoryHistoryResponse> history(@PathVariable Long productId) {
        return service.history(productId);
    }
}
