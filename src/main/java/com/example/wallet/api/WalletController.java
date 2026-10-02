package com.example.wallet.api;

import com.example.wallet.domain.Wallet;
import com.example.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    public record AmountRequest(BigDecimal amount) {}

    public record WalletResponse(UUID id, BigDecimal balance, boolean locked) {
        static WalletResponse of(Wallet w) {
            return new WalletResponse(w.getId(), w.getBalance(), w.isLocked());
        }
    }

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse open() {
        return WalletResponse.of(service.open());
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable UUID id) {
        return WalletResponse.of(service.get(id));
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(@PathVariable UUID id,
                                  @RequestHeader("Idempotency-Key") String key,
                                  @RequestBody AmountRequest body) {
        return WalletResponse.of(service.deposit(id, key, body.amount()));
    }

    @PostMapping("/{id}/withdraw")
    public WalletResponse withdraw(@PathVariable UUID id,
                                   @RequestHeader("Idempotency-Key") String key,
                                   @RequestBody AmountRequest body) {
        return WalletResponse.of(service.withdraw(id, key, body.amount()));
    }

    @PostMapping("/{id}/lock")
    public WalletResponse lock(@PathVariable UUID id) {
        return WalletResponse.of(service.lockWallet(id));
    }
}
