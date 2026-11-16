package com.papercopilot.core.marketdata;

import java.util.Optional;

/**
 * The only source of prices in the system. Swap implementations (broker API, licensed vendor)
 * without touching the screener, ledger or AI layer.
 */
public interface MarketDataProvider {

    /**
     * Current data for one symbol, fetching from the source if needed. Empty when the symbol is unknown
     * to the provider or the upstream call failed. Used for trades and single lookups.
     */
    Optional<InstrumentSnapshot> snapshot(String symbol);

    /**
     * Cheap lookup for scans over the whole universe (screener, market list). Must not trigger one
     * upstream request per symbol: a remote provider answers from what it already has, which may be
     * older than snapshot() would return, or empty if the symbol has not been loaded yet.
     */
    default Optional<InstrumentSnapshot> scanSnapshot(String symbol) {
        return snapshot(symbol);
    }

    default Optional<Quote> quote(String symbol) {
        return snapshot(symbol).map(InstrumentSnapshot::quote);
    }
}
