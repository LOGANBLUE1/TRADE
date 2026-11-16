package com.papercopilot.core.marketdata;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.papercopilot.core.error.DomainException;
import com.papercopilot.core.error.ErrorCode;

/** Browsing and search over the allowed universe. */
@Service
public class InstrumentService {

    /** Up to this many results, each one is looked up individually so a search always shows prices. */
    private static final int DIRECT_LOOKUP_LIMIT = 10;

    private final MarketDataProvider provider;
    private final SymbolUniverse universe;

    public InstrumentService(MarketDataProvider provider, SymbolUniverse universe) {
        this.provider = provider;
        this.universe = universe;
    }

    /**
     * Allowed instruments, equities first. With a query, only those whose symbol, name or industry
     * contains it (case-insensitive); matches on the symbol come first. limit null means all.
     */
    public List<InstrumentView> list(String query, Integer limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<InstrumentInfo> found = Stream.concat(universe.equities().stream(), universe.fx().stream())
                .filter(i -> q.isEmpty() || rank(i, q) < 3)
                .sorted(java.util.Comparator.comparingInt(i -> q.isEmpty() ? 0 : rank(i, q)))
                .limit(limit == null ? Long.MAX_VALUE : Math.max(1, limit))
                .toList();
        boolean direct = found.size() <= DIRECT_LOOKUP_LIMIT;
        return found.stream()
                .map(i -> InstrumentView.of(i, (direct ? provider.snapshot(i.symbol())
                        .or(() -> provider.scanSnapshot(i.symbol())) : provider.scanSnapshot(i.symbol()))
                        .orElse(null)))
                .toList();
    }

    public InstrumentView get(String symbolInput) {
        String symbol = universe.resolve(symbolInput).orElseThrow(() -> new DomainException(
                ErrorCode.SYMBOL_NOT_ALLOWED, "Symbol is not in the allowed universe: " + symbolInput));
        // Prefer current data; if the source is unreachable, older data is better than an error page.
        InstrumentSnapshot snap = provider.snapshot(symbol).or(() -> provider.scanSnapshot(symbol))
                .orElseThrow(() -> new DomainException(ErrorCode.QUOTE_UNAVAILABLE,
                        "No data available right now for " + symbol));
        return InstrumentView.of(universe.info(symbol).orElseThrow(), snap);
    }

    /** -1: exact ticker, 0: symbol starts with the query, 1: symbol or name contains it, 2: industry contains it, 3: no match. */
    private static int rank(InstrumentInfo i, String q) {
        String symbol = i.symbol().toLowerCase(Locale.ROOT);
        if (symbol.equals(q) || symbol.startsWith(q + ".") || symbol.startsWith(q + "=")) {
            return -1;   // the exact ticker, e.g. "m&m" before "m&mfin"
        }
        if (symbol.startsWith(q)) {
            return 0;
        }
        if (symbol.contains(q) || i.name().toLowerCase(Locale.ROOT).contains(q)) {
            return 1;
        }
        return i.industry() != null && i.industry().toLowerCase(Locale.ROOT).contains(q) ? 2 : 3;
    }
}
