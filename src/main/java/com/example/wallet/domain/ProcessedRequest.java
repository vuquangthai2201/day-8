package com.example.wallet.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "processed_request")
public class ProcessedRequest {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    protected ProcessedRequest() {}

    public ProcessedRequest(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
