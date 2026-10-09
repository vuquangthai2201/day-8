package com.example.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** version = phien ban ma client dang giu (lay tu lan GET/PUT truoc do). */
public record UpdateInventoryRequest(
        @NotBlank String productName,
        @NotNull Long version) {
}
