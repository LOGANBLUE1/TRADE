package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;
    private String userId;
    private BigDecimal cash;
    private String currency;

    protected Account() {}

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public BigDecimal getCash() { return cash; }
    public String getCurrency() { return currency; }

    void setCash(BigDecimal cash) { this.cash = cash; }
}
