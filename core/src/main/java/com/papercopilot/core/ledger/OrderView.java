package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderView(
        UUID id,
        String symbol,
        OrderSide side,
        long quantity,
        OrderStatus status,
        OrderSource source,
        BigDecimal referencePrice,
        BigDecimal fillPrice,
        String rationale,
        Instant createdAt,
        Instant approvedAt,
        String approvedBy,
        Instant filledAt) {

    static OrderView of(TradeOrder o) {
        return new OrderView(o.getId(), o.getSymbol(), o.getSide(), o.getQuantity(), o.getStatus(), o.getSource(),
                o.getReferencePrice(), o.getFillPrice(), o.getRationale(), o.getCreatedAt(), o.getApprovedAt(),
                o.getApprovedBy(), o.getFilledAt());
    }
}
