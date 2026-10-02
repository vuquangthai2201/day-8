package com.example.wallet.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** Bản ghi outbox: ghi cùng transaction với thay đổi số dư, relay sẽ đẩy lên Kafka. */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String payload;

    protected OutboxEvent() {}

    public OutboxEvent(UUID aggregateId, String type, String payload) {
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
    }
}
