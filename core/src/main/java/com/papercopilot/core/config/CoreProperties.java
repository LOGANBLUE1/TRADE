package com.papercopilot.core.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("copilot")
public record CoreProperties(MarketData marketData, Universe universe, Trading trading) {

    /**
     * provider: "simulated" (generated numbers) or "yahoo" (delayed, unofficial).
     * cacheTtl: how old a quote may be when used for a trade or a single lookup.
     * warmInterval / warmPause: for remote providers, how often the whole universe is re-fetched in the
     * background and how long to wait between two requests, to stay under the provider's rate limit.
     */
    public record MarketData(String provider, Duration cacheTtl, Duration warmInterval, Duration warmPause) {}

    /**
     * equitiesFile: a CSV in NSE's index-constituent format (Company Name,Industry,Symbol,Series,ISIN Code).
     * Equities are tradable; fx symbols are quote-only for now.
     */
    public record Universe(String equitiesFile, List<String> fx) {}

    public record Trading(
            BigDecimal startingCash,
            BigDecimal maxOrderValue,
            long maxOrderQuantity,
            int maxDailyOrders,
            Duration approvalTtl,
            BigDecimal maxSlippagePct) {}
}
