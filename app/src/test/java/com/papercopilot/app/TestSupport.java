package com.papercopilot.app;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.papercopilot.core.marketdata.InstrumentSnapshot;
import com.papercopilot.core.marketdata.MarketDataProvider;
import com.papercopilot.core.marketdata.Quote;
import com.papercopilot.core.marketdata.SimulatedMarketDataProvider;
import com.papercopilot.core.marketdata.SymbolUniverse;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupport {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    MutableMarketData testMarketData(MutableClock clock, SymbolUniverse universe) {
        return new MutableMarketData(clock, universe);
    }

    @Bean
    StubChatModel stubChatModel() {
        return new StubChatModel();
    }

    public static class MutableClock extends Clock {

        private volatile Duration offset = Duration.ZERO;

        public void advance(Duration d) { offset = offset.plus(d); }
        public void reset() { offset = Duration.ZERO; }

        @Override public Instant instant() { return Instant.now().plus(offset); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    /** Simulated data with per-symbol price overrides, no cache. */
    public static class MutableMarketData implements MarketDataProvider {

        private final SimulatedMarketDataProvider delegate;
        private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();

        MutableMarketData(Clock clock, SymbolUniverse universe) {
            this.delegate = new SimulatedMarketDataProvider(clock, universe);
        }

        public void setPrice(String symbol, String price) { prices.put(symbol, new BigDecimal(price)); }
        public void reset() { prices.clear(); }

        @Override
        public Optional<InstrumentSnapshot> snapshot(String symbol) {
            return delegate.snapshot(symbol).map(s -> {
                BigDecimal p = prices.get(symbol);
                if (p == null) {
                    return s;
                }
                Quote q = s.quote();
                Quote moved = new Quote(q.symbol(), q.name(), p, q.previousClose(), q.changePct(), q.currency(),
                        q.asOf(), q.delayed(), q.source());
                return new InstrumentSnapshot(s.symbol(), s.name(), p, s.changePct(), s.pe(), s.marketCapCr(),
                        s.volume(), s.avgVolume20d(), s.sma50(), s.sma200(), moved);
            });
        }
    }

    /** Stands in for the LLM: returns a canned answer, or fails like a provider outage. */
    public static class StubChatModel implements ChatModel {

        public volatile String reply = "{}";
        public volatile boolean down = false;
        public final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            if (down) {
                throw new IllegalStateException("simulated LLM outage");
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }
}
