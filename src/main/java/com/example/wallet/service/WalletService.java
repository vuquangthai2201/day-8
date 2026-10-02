package com.example.wallet.service;

import com.example.wallet.domain.*;
import com.example.wallet.repo.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final ProcessedRequestRepository processed;
    private final OutboxRepository outbox;

    public WalletService(WalletRepository wallets, ProcessedRequestRepository processed, OutboxRepository outbox) {
        this.wallets = wallets;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public Wallet open() {
        return wallets.save(Wallet.open());
    }

    @Transactional(readOnly = true)
    public Wallet get(UUID id) {
        return wallets.findById(id).orElseThrow(() -> new WalletNotFoundException(id));
    }

    @Transactional
    public Wallet deposit(UUID id, String idempotencyKey, BigDecimal amount) {
        Wallet w = lock(id);
        if (processed.existsById(idempotencyKey)) return w; // retry: không áp dụng lần hai
        w.deposit(amount);
        record(idempotencyKey, w, "MONEY_DEPOSITED", amount);
        return w;
    }

    @Transactional
    public Wallet withdraw(UUID id, String idempotencyKey, BigDecimal amount) {
        Wallet w = lock(id);
        if (processed.existsById(idempotencyKey)) return w;
        w.withdrawMoney(amount);
        record(idempotencyKey, w, "MONEY_WITHDRAWN", amount);
        return w;
    }

    @Transactional
    public Wallet lockWallet(UUID id) {
        Wallet w = lock(id);
        w.lockWallet();
        outbox.save(new OutboxEvent(w.getId(), "WALLET_LOCKED", "{}"));
        return w;
    }

    private Wallet lock(UUID id) {
        return wallets.findByIdForUpdate(id).orElseThrow(() -> new WalletNotFoundException(id));
    }

    /** Khóa idempotency và sự kiện outbox nằm trong cùng transaction với thay đổi số dư. */
    private void record(String key, Wallet w, String type, BigDecimal amount) {
        processed.save(new ProcessedRequest(key));
        String payload = "{\"walletId\":\"" + w.getId() + "\",\"amount\":" + amount.toPlainString() + "}";
        outbox.save(new OutboxEvent(w.getId(), type, payload));
    }
}
