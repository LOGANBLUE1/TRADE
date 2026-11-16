package com.papercopilot.core.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID accountId;
    private UUID orderId;
    private String eventType;
    private String actor;
    private String detail;
    private Instant createdAt;

    protected AuditEvent() {}

    AuditEvent(UUID accountId, UUID orderId, String eventType, String actor, String detail, Instant createdAt) {
        this.accountId = accountId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.actor = actor;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public String getActor() { return actor; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
