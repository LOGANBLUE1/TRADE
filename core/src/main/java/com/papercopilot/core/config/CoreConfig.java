package com.papercopilot.core.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.papercopilot.core.marketdata.CachingMarketDataProvider;
import com.papercopilot.core.marketdata.MarketDataProvider;
import com.papercopilot.core.marketdata.SimulatedMarketDataProvider;
import com.papercopilot.core.marketdata.YahooMarketDataProvider;

@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MarketDataProvider marketDataProvider(CoreProperties props, Clock clock) {
        MarketDataProvider raw = switch (props.marketData().provider()) {
            case "simulated" -> new SimulatedMarketDataProvider(clock);
            case "yahoo" -> new YahooMarketDataProvider();
            default -> throw new IllegalStateException(
                    "Unknown copilot.market-data.provider: " + props.marketData().provider());
        };
        return new CachingMarketDataProvider(raw, props.marketData().cacheTtl());
    }
}
