package com.example.flashsale.error;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(long id) {
        super("Không tìm thấy sản phẩm hoặc đợt sale chưa mở: " + id);
    }
}
