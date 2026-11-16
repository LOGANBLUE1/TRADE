package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    private UUID id;
    private UUID accountId;
    private String symbol;
    private long quantity;
    private BigDecimal avgCost;

    protected Position() {}

    Position(UUID accountId, String symbol) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.symbol = symbol;
        this.avgCost = BigDecimal.ZERO;
    }

    public String getSymbol() { return symbol; }
    public long getQuantity() { return quantity; }
    public BigDecimal getAvgCost() { return avgCost; }

    void setQuantity(long quantity) { this.quantity = quantity; }
    void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
}
