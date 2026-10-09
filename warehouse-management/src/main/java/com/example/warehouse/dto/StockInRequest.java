package com.example.warehouse.dto;

import jakarta.validation.constraints.Min;

public record StockInRequest(@Min(value = 1, message = "quantity phai >= 1") int quantity) {
}
