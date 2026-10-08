package com.example.productcache.controller;

import com.example.productcache.dto.ProductRequest;
import com.example.productcache.dto.ProductResponse;
import com.example.productcache.facade.ProductFacade;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductFacade facade;

    public ProductController(ProductFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return facade.getProduct(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = facade.createProduct(request);
        return ResponseEntity.created(URI.create("/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return facade.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        facade.deleteProduct(id);
        return ResponseEntity.noContent().build();   // 204
    }
}
