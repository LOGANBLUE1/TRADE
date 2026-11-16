package com.papercopilot.app.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.papercopilot.core.marketdata.Quote;
import com.papercopilot.core.marketdata.QuoteService;
import com.papercopilot.core.screener.FilterSpec;
import com.papercopilot.core.screener.ScreenResult;
import com.papercopilot.core.screener.ScreenerService;

@RestController
@RequestMapping("/api")
public class MarketController {

    private final QuoteService quotes;
    private final ScreenerService screener;

    public MarketController(QuoteService quotes, ScreenerService screener) {
        this.quotes = quotes;
        this.screener = screener;
    }

    @GetMapping("/quotes/{symbol}")
    public Quote quote(@PathVariable String symbol) {
        return quotes.getQuote(symbol);
    }

    /** The manual filter builder posts the same FilterSpec the AI would have produced. */
    @PostMapping("/screens/run")
    public ScreenResult run(@RequestBody FilterSpec filter) {
        return screener.run(filter);
    }
}
