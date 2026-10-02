package com.example.flashsale.error;

public class SoldOutException extends RuntimeException {
    public SoldOutException() {
        super("Hết hàng");
    }
}
