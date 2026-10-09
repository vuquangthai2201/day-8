package com.example.warehouse.exception;

public class DuplicateProductException extends RuntimeException {
    public DuplicateProductException(Long productId) {
        super("Product already exists: " + productId);
    }
}
