package com.papercopilot.core.marketdata;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/** Short TTL cache in front of a provider. Failed lookups are not cached. */
public class CachingMarketDataProvider implements MarketDataProvider {

    private final MarketDataProvider delegate;
    private final Cache<String, InstrumentSnapshot> cache;

    public CachingMarketDataProvider(MarketDataProvider delegate, Duration ttl) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(5_000).build();
    }

    @Override
    public Optional<InstrumentSnapshot> snapshot(String symbol) {
        InstrumentSnapshot cached = cache.getIfPresent(symbol);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<InstrumentSnapshot> fresh = delegate.snapshot(symbol);
        fresh.ifPresent(s -> cache.put(symbol, s));
        return fresh;
    }
}
