package com.papercopilot.core.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Delayed (~15 min) prices from Yahoo Finance's unofficial chart endpoint, using .NS / .BO / =X symbols.
 * One call per symbol returns a year of daily bars, from which price, volume and moving averages are
 * derived. The chart endpoint has no fundamentals, so PE and market cap are null here and screens on
 * those fields match nothing until a fundamentals source is added.
 *
 * Unofficial and unlicensed: fine for a personal demo, not for a commercial product.
 */
public class YahooMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooMarketDataProvider.class);

    private final RestClient http;

    public YahooMarketDataProvider() {
        this(RestClient.builder()
                .baseUrl("https://query1.finance.yahoo.com")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build());
    }

    YahooMarketDataProvider(RestClient http) {
        this.http = http;
    }

    @Override
    public Optional<InstrumentSnapshot> snapshot(String symbol) {
        try {
            JsonNode body = http.get()
                    .uri("/v8/finance/chart/{symbol}?range=1y&interval=1d", symbol)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(symbol, body);
        } catch (RuntimeException e) {
            log.warn("Yahoo lookup failed for {}: {}", symbol, e.toString());
            return Optional.empty();
        }
    }

    static Optional<InstrumentSnapshot> parse(String symbol, JsonNode body) {
        JsonNode result = body == null ? null : body.path("chart").path("result").path(0);
        if (result == null || result.isMissingNode() || !result.path("meta").has("regularMarketPrice")) {
            return Optional.empty();
        }
        JsonNode meta = result.path("meta");
        List<Double> closes = numbers(result.path("indicators").path("quote").path(0).path("close"));
        List<Double> volumes = numbers(result.path("indicators").path("quote").path(0).path("volume"));

        BigDecimal price = BigDecimal.valueOf(meta.path("regularMarketPrice").asDouble());
        // The last daily bar is the current session, so the previous close is the bar before it.
        BigDecimal prev = closes.size() >= 2 ? BigDecimal.valueOf(closes.get(closes.size() - 2)) : null;
        BigDecimal changePct = prev == null || prev.signum() == 0 ? null
                : price.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 2, RoundingMode.HALF_UP);
        String name = meta.path("longName").asText(meta.path("shortName").asText(symbol));
        Instant asOf = Instant.ofEpochSecond(meta.path("regularMarketTime").asLong(Instant.now().getEpochSecond()));

        Quote quote = new Quote(symbol, name, price, prev, changePct,
                meta.path("currency").asText("INR"), asOf, true, "YAHOO");
        BigDecimal volume = volumes.isEmpty() ? null : BigDecimal.valueOf(volumes.get(volumes.size() - 1));
        return Optional.of(new InstrumentSnapshot(symbol, name, price, changePct, null, null, volume,
                // Average excludes the current, still-forming session.
                trailingAverage(volumes.subList(0, Math.max(0, volumes.size() - 1)), 20),
                trailingAverage(closes, 50), trailingAverage(closes, 200), quote));
    }

    private static List<Double> numbers(JsonNode array) {
        List<Double> out = new ArrayList<>();
        for (JsonNode n : array) {
            if (n.isNumber()) {
                out.add(n.asDouble());
            }
        }
        return out;
    }

    /** Null when there is not enough history for a full window. */
    private static BigDecimal trailingAverage(List<Double> values, int window) {
        if (values.size() < window) {
            return null;
        }
        double sum = 0;
        for (double v : values.subList(values.size() - window, values.size())) {
            sum += v;
        }
        return BigDecimal.valueOf(sum / window).setScale(2, RoundingMode.HALF_UP);
    }
}
