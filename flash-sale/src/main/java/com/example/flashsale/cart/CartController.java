package com.example.flashsale.cart;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CartController {

    public record AddRequest(long productId, int qty) {}

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @PostMapping("/cart/items")
    public ResponseEntity<CartService.HoldResult> add(@RequestHeader("X-User-Id") String userId,
                                                      @RequestBody AddRequest body) {
        CartService.HoldResult r = service.add(userId, body.productId(), body.qty());
        return ResponseEntity.status(r.alreadyHeld() ? HttpStatus.OK : HttpStatus.CREATED).body(r);
    }
}
