package com.example.wallet.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Aggregate Root. Trạng thái chỉ thay đổi qua các phương thức nghiệp vụ. */
@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private boolean locked;

    @Version
    private long version;

    protected Wallet() {} // dành cho JPA

    public static Wallet open() {
        Wallet w = new Wallet();
        w.id = UUID.randomUUID();
        w.balance = BigDecimal.ZERO;
        return w;
    }

    public void deposit(BigDecimal amount) {
        requirePositive(amount);
        if (locked) throw new DomainException("Ví điện tử hiện đang bị khóa!");
        balance = balance.add(amount);
    }

    public void withdrawMoney(BigDecimal amount) {
        requirePositive(amount);
        if (locked) throw new DomainException("Ví điện tử hiện đang bị khóa!");
        if (balance.compareTo(amount) < 0) throw new DomainException("Số dư không đủ!");
        balance = balance.subtract(amount);
    }

    public void lockWallet() {
        locked = true;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException("Số tiền phải lớn hơn 0!");
        }
    }

    public UUID getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public boolean isLocked() { return locked; }
}
