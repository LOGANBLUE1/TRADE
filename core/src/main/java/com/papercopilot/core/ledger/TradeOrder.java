package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class TradeOrder {

    @Id
    private UUID id;
    private UUID accountId;
    private String symbol;
    @Enumerated(EnumType.STRING)
    private OrderSide side;
    private long quantity;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    private OrderSource source;
    private String idempotencyKey;
    private BigDecimal referencePrice;
    private BigDecimal fillPrice;
    private String rationale;
    private Instant createdAt;
    private Instant approvedAt;
    private String approvedBy;
    private Instant filledAt;

    protected TradeOrder() {}

    TradeOrder(UUID accountId, String symbol, OrderSide side, long quantity, OrderSource source,
               String idempotencyKey, BigDecimal referencePrice, String rationale, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING_APPROVAL;
        this.source = source;
        this.idempotencyKey = idempotencyKey;
        this.referencePrice = referencePrice;
        this.rationale = rationale;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public long getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public OrderSource getSource() { return source; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public BigDecimal getFillPrice() { return fillPrice; }
    public String getRationale() { return rationale; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getFilledAt() { return filledAt; }

    void setStatus(OrderStatus status) { this.status = status; }

    void approve(String actor, Instant at) {
        this.status = OrderStatus.APPROVED;
        this.approvedBy = actor;
        this.approvedAt = at;
    }

    void fill(BigDecimal price, Instant at) {
        this.status = OrderStatus.FILLED;
        this.fillPrice = price;
        this.filledAt = at;
    }
}
