package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioView(
        BigDecimal cash,
        String currency,
        List<PositionView> positions,
        BigDecimal positionsValue,
        BigDecimal totalEquity,
        Instant asOf) {

    /** lastPrice, marketValue and unrealizedPnl are null when no quote is available right now. */
    public record PositionView(
            String symbol,
            long quantity,
            BigDecimal avgCost,
            BigDecimal lastPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl) {}
}
