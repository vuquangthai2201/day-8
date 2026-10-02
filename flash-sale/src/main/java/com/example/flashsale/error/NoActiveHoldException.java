package com.example.flashsale.error;

public class NoActiveHoldException extends RuntimeException {
    public NoActiveHoldException() {
        super("Không có hold còn hiệu lực, hãy add-to-cart lại");
    }
}
