package com.papercopilot.core.marketdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.papercopilot.core.error.DomainException;
import com.papercopilot.core.error.ErrorCode;

/** Browsing and search over the allowed universe. */
@Service
public class InstrumentService {

    private final MarketDataProvider provider;
    private final SymbolUniverse universe;

    public InstrumentService(MarketDataProvider provider, SymbolUniverse universe) {
        this.provider = provider;
        this.universe = universe;
    }

    /**
     * Every allowed instrument that has data right now, equities first. With a query, only those whose
     * symbol or name contains it (case-insensitive).
     */
    public List<InstrumentView> list(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<InstrumentView> out = new ArrayList<>();
        collect(universe.equities(), "EQUITY", q, out);
        collect(universe.fx(), "FX", q, out);
        return out;
    }

    public InstrumentView get(String symbolInput) {
        String symbol = universe.resolve(symbolInput).orElseThrow(() -> new DomainException(
                ErrorCode.SYMBOL_NOT_ALLOWED, "Symbol is not in the allowed universe: " + symbolInput));
        InstrumentSnapshot snap = provider.snapshot(symbol).orElseThrow(() -> new DomainException(
                ErrorCode.QUOTE_UNAVAILABLE, "No data available right now for " + symbol));
        return view(symbol, snap);
    }

    private void collect(Iterable<String> symbols, String type, String q, List<InstrumentView> out) {
        for (String symbol : symbols) {
            provider.snapshot(symbol)
                    .filter(s -> q.isEmpty() || symbol.toLowerCase(Locale.ROOT).contains(q)
                            || (s.name() != null && s.name().toLowerCase(Locale.ROOT).contains(q)))
                    .ifPresent(s -> out.add(new InstrumentView(type, universe.isTradable(symbol), s)));
        }
    }

    private InstrumentView view(String symbol, InstrumentSnapshot snap) {
        boolean equity = universe.equities().contains(symbol);
        return new InstrumentView(equity ? "EQUITY" : "FX", universe.isTradable(symbol), snap);
    }
}
