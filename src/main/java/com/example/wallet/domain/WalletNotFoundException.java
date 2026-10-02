package com.example.wallet.domain;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(UUID id) {
        super("Không tìm thấy ví " + id);
    }
}
