package com.papercopilot.core.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("copilot")
public record CoreProperties(MarketData marketData, Universe universe, Trading trading) {

    /** provider: "simulated" (fake static prices) or "yahoo" (delayed, unofficial). */
    public record MarketData(String provider, Duration cacheTtl) {}

    /** equities are tradable; fx symbols are quote-only for now. */
    public record Universe(List<String> equities, List<String> fx) {}

    public record Trading(
            BigDecimal startingCash,
            BigDecimal maxOrderValue,
            long maxOrderQuantity,
            int maxDailyOrders,
            Duration approvalTtl,
            BigDecimal maxSlippagePct) {}
}
