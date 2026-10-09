package com.example.warehouse.dto;

import java.time.LocalDateTime;

public record InventoryHistoryResponse(
        int revision,
        String revisionType,      // ADD / MOD / DEL
        LocalDateTime changedAt,
        String productName,
        int quantity,             // so luong sau thay doi
        int quantityChange) {     // chenh lech so voi revision truoc
}
