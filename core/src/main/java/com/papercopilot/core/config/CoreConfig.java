package com.papercopilot.core.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.papercopilot.core.marketdata.CachingMarketDataProvider;
import com.papercopilot.core.marketdata.MarketDataProvider;
import com.papercopilot.core.marketdata.SimulatedMarketDataProvider;
import com.papercopilot.core.marketdata.SymbolUniverse;
import com.papercopilot.core.marketdata.UniverseWarmer;
import com.papercopilot.core.marketdata.YahooMarketDataProvider;

@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MarketDataProvider marketDataProvider(CoreProperties props, SymbolUniverse universe, Clock clock) {
        CoreProperties.MarketData cfg = props.marketData();
        switch (cfg.provider()) {
            case "simulated":
                // Generated locally, so no cache or warm-up is needed.
                return new SimulatedMarketDataProvider(clock, universe);
            case "yahoo":
                CachingMarketDataProvider cached =
                        new CachingMarketDataProvider(new YahooMarketDataProvider(), cfg.cacheTtl(), clock);
                new UniverseWarmer(cached, universe, cfg).start();
                return cached;
            default:
                throw new IllegalStateException("Unknown copilot.market-data.provider: " + cfg.provider());
        }
    }
}
