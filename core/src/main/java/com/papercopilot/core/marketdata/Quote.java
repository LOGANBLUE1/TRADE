package com.papercopilot.core.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal changePct,
        String currency,
        Instant asOf,
        boolean delayed,
        String source) {}
