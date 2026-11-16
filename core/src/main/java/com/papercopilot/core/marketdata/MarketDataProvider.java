package com.papercopilot.core.marketdata;

import java.util.Optional;

/**
 * The only source of prices in the system. Swap implementations (broker API, licensed vendor)
 * without touching the screener, ledger or AI layer.
 */
public interface MarketDataProvider {

    /** Empty when the symbol is unknown to the provider or the upstream call failed. */
    Optional<InstrumentSnapshot> snapshot(String symbol);

    default Optional<Quote> quote(String symbol) {
        return snapshot(symbol).map(InstrumentSnapshot::quote);
    }
}
