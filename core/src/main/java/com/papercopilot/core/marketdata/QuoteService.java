package com.papercopilot.core.marketdata;

import org.springframework.stereotype.Service;

import com.papercopilot.core.error.DomainException;
import com.papercopilot.core.error.ErrorCode;

@Service
public class QuoteService {

    private final MarketDataProvider provider;
    private final SymbolUniverse universe;

    public QuoteService(MarketDataProvider provider, SymbolUniverse universe) {
        this.provider = provider;
        this.universe = universe;
    }

    public Quote getQuote(String symbolInput) {
        String symbol = universe.resolve(symbolInput).orElseThrow(() -> new DomainException(
                ErrorCode.SYMBOL_NOT_ALLOWED, "Symbol is not in the allowed universe: " + symbolInput));
        return provider.quote(symbol).orElseThrow(() -> new DomainException(
                ErrorCode.QUOTE_UNAVAILABLE, "No quote available right now for " + symbol));
    }
}
