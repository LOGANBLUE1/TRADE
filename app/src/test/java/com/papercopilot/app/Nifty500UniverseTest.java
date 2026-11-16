package com.papercopilot.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.papercopilot.core.config.CoreProperties;
import com.papercopilot.core.marketdata.CachingMarketDataProvider;
import com.papercopilot.core.marketdata.InstrumentService;
import com.papercopilot.core.marketdata.InstrumentSnapshot;
import com.papercopilot.core.marketdata.MarketDataProvider;
import com.papercopilot.core.marketdata.SimulatedMarketDataProvider;
import com.papercopilot.core.marketdata.SymbolUniverse;
import com.papercopilot.core.screener.FilterSpec;
import com.papercopilot.core.screener.FilterSpec.Condition;
import com.papercopilot.core.screener.FilterSpec.Field;
import com.papercopilot.core.screener.FilterSpec.Op;
import com.papercopilot.core.screener.ScreenResult;
import com.papercopilot.core.screener.ScreenerService;

/** The shipped NIFTY 500 file, without a Spring context. */
class Nifty500UniverseTest {

    final Clock clock = Clock.systemUTC();
    final SymbolUniverse universe = new SymbolUniverse(new CoreProperties(null,
            new CoreProperties.Universe("classpath:universe/nifty500.csv", List.of("GBPUSD=X")), null));
    final SimulatedMarketDataProvider simulated = new SimulatedMarketDataProvider(clock, universe);

    @Test
    void loadsTheWholeIndex_withNamesAndIndustries() {
        assertThat(universe.equities()).hasSizeBetween(495, 505);
        assertThat(universe.resolve("reliance")).contains("RELIANCE.NS");
        assertThat(universe.resolve("m&m")).contains("M&M.NS");
        assertThat(universe.resolve("bajaj-auto")).contains("BAJAJ-AUTO.NS");
        assertThat(universe.info("TCS.NS").orElseThrow().industry()).isEqualTo("Information Technology");
        assertThat(universe.resolve("NOTLISTED")).isEmpty();
    }

    @Test
    void simulatedProviderCoversEveryStock_withStableNumbers() {
        universe.equities().forEach(i -> {
            InstrumentSnapshot s = simulated.snapshot(i.symbol()).orElseThrow();
            assertThat(s.price().signum()).as(i.symbol()).isPositive();
            assertThat(s.name()).isEqualTo(i.name());
            assertThat(simulated.snapshot(i.symbol()).orElseThrow().price()).isEqualByComparingTo(s.price());
        });
    }

    @Test
    void screenerScansAllStocks_andReportsTotalBeyondTheLimit() {
        ScreenResult r = new ScreenerService(simulated, universe, clock).run(new FilterSpec(
                List.of(new Condition(Field.PE, Op.LT, new java.math.BigDecimal("30"), null)), Field.PE, false, 10));
        assertThat(r.universeSize()).isEqualTo(universe.equities().size());
        assertThat(r.matches()).hasSize(10);
        assertThat(r.totalMatches()).isGreaterThan(10);
        assertThat(r.unavailable()).isZero();
    }

    /** With a remote provider, scanning 500 stocks must not turn into 500 upstream calls. */
    @Test
    void scansNeverCallUpstream_andOnlyUseWhatTheWarmerLoaded() {
        AtomicInteger upstreamCalls = new AtomicInteger();
        MarketDataProvider remote = symbol -> {
            upstreamCalls.incrementAndGet();
            return simulated.snapshot(symbol);
        };
        CachingMarketDataProvider cached = new CachingMarketDataProvider(remote, Duration.ofSeconds(60), clock);
        ScreenerService screener = new ScreenerService(cached, universe, clock);
        FilterSpec all = new FilterSpec(List.of(), null, null, null);

        ScreenResult cold = screener.run(all);
        assertThat(upstreamCalls).hasValue(0);
        assertThat(cold.unavailable()).isEqualTo(universe.equities().size());

        cached.refresh("TCS.NS");
        cached.refresh("INFY.NS");
        ScreenResult warm = screener.run(all);
        assertThat(upstreamCalls).hasValue(2);
        assertThat(warm.totalMatches()).isEqualTo(2);

        // The full market list is also served from the cache; a short search looks prices up directly.
        InstrumentService instruments = new InstrumentService(cached, universe);
        assertThat(instruments.list(null, null)).hasSize(universe.equities().size() + 1);
        assertThat(upstreamCalls).hasValue(2);
        assertThat(instruments.list("wipro", 8)).isNotEmpty().allMatch(v -> v.data() != null);
        assertThat(upstreamCalls.get()).isBetween(3, 10);
    }

    @Test
    void aFailedRefreshKeepsOldDataForBrowsing_butNotForTrading() {
        boolean[] down = {false};
        MarketDataProvider flaky = symbol -> down[0] ? Optional.empty() : simulated.snapshot(symbol);
        CachingMarketDataProvider cached = new CachingMarketDataProvider(flaky, Duration.ZERO, clock);
        assertThat(cached.snapshot("TCS.NS")).isPresent();
        down[0] = true;
        assertThat(cached.snapshot("TCS.NS")).isEmpty();        // TTL passed and the source is down
        assertThat(cached.scanSnapshot("TCS.NS")).isPresent();   // last known data still browsable
    }
}
