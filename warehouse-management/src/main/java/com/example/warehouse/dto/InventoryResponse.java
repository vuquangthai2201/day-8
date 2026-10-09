package com.example.warehouse.dto;

import com.example.warehouse.entity.Inventory;

public record InventoryResponse(Long productId, String productName, int quantity, Long version) {

    public static InventoryResponse from(Inventory i) {
        return new InventoryResponse(i.getProductId(), i.getProductName(), i.getQuantity(), i.getVersion());
    }
}
