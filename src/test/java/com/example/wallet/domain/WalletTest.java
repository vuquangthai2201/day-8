package com.example.wallet.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WalletTest {

    private static Wallet walletWith(String amount) {
        Wallet w = Wallet.open();
        w.deposit(new BigDecimal(amount));
        return w;
    }

    @Test
    void withdrawReducesBalance() {
        Wallet w = walletWith("100");
        w.withdrawMoney(new BigDecimal("40"));
        assertEquals(0, new BigDecimal("60").compareTo(w.getBalance()));
    }

    @Test
    void cannotWithdrawMoreThanBalance() {
        Wallet w = walletWith("100");
        assertThrows(DomainException.class, () -> w.withdrawMoney(new BigDecimal("100.01")));
        assertEquals(0, new BigDecimal("100").compareTo(w.getBalance()));
    }

    @Test
    void cannotWithdrawWhenLocked() {
        Wallet w = walletWith("100");
        w.lockWallet();
        assertThrows(DomainException.class, () -> w.withdrawMoney(BigDecimal.TEN));
    }

    @Test
    void rejectsNonPositiveAmount() {
        Wallet w = walletWith("100");
        assertThrows(DomainException.class, () -> w.withdrawMoney(BigDecimal.ZERO));
        assertThrows(DomainException.class, () -> w.withdrawMoney(new BigDecimal("-5")));
    }
}
