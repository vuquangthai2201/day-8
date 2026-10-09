package com.example.warehouse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInventoryRequest(
        @NotNull Long productId,
        @NotBlank String productName,
        @Min(0) int quantity) {
}
