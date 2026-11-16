package com.papercopilot.core.marketdata;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache in front of a remote provider.
 *
 * snapshot(): returns the cached value while it is younger than the TTL, otherwise fetches. A failed
 * fetch returns empty, so a trade never fills on a price older than the TTL.
 * scanSnapshot(): returns whatever is cached, however old, and never calls upstream. The background
 * warmer (UniverseWarmer) keeps it filled through refresh().
 */
public class CachingMarketDataProvider implements MarketDataProvider {

    private record Entry(InstrumentSnapshot snapshot, Instant fetchedAt) {}

    private final MarketDataProvider delegate;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingMarketDataProvider(MarketDataProvider delegate, Duration ttl, Clock clock) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public Optional<InstrumentSnapshot> snapshot(String symbol) {
        Entry e = cache.get(symbol);
        if (e != null && e.fetchedAt().plus(ttl).isAfter(clock.instant())) {
            return Optional.of(e.snapshot());
        }
        return refresh(symbol);
    }

    @Override
    public Optional<InstrumentSnapshot> scanSnapshot(String symbol) {
        return Optional.ofNullable(cache.get(symbol)).map(Entry::snapshot);
    }

    /** Fetches from upstream and stores the result. On failure the previous cached value is kept. */
    public Optional<InstrumentSnapshot> refresh(String symbol) {
        Optional<InstrumentSnapshot> fresh = delegate.snapshot(symbol);
        fresh.ifPresent(s -> cache.put(symbol, new Entry(s, clock.instant())));
        return fresh;
    }

    public int cachedCount() {
        return cache.size();
    }
}
