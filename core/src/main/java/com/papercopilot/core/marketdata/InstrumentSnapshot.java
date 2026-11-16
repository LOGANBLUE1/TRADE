package com.papercopilot.core.marketdata;

import java.math.BigDecimal;

/** Everything the screener can filter on. Any field except symbol and price may be null. */
public record InstrumentSnapshot(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal changePct,
        BigDecimal pe,
        BigDecimal marketCapCr,
        BigDecimal volume,
        BigDecimal avgVolume20d,
        BigDecimal sma50,
        BigDecimal sma200,
        Quote quote) {}
