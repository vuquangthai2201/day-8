package com.example.flashsale.error;

public class AlreadyPurchasedException extends RuntimeException {
    public AlreadyPurchasedException() {
        super("Bạn đã mua sản phẩm này");
    }
}
