package com.papercopilot.core.marketdata;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.papercopilot.core.config.CoreProperties;

/**
 * Keeps a remote provider's cache filled for the whole universe, so the screener and market list never
 * make hundreds of upstream calls on a user's request. One request at a time with a pause between them;
 * a pass is abandoned after several failures in a row (typically the provider rate-limiting us) and
 * retried at the next interval.
 */
public class UniverseWarmer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UniverseWarmer.class);
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final CachingMarketDataProvider cache;
    private final SymbolUniverse universe;
    private final Duration interval;
    private final Duration pause;

    public UniverseWarmer(CachingMarketDataProvider cache, SymbolUniverse universe, CoreProperties.MarketData cfg) {
        this.cache = cache;
        this.universe = universe;
        this.interval = cfg.warmInterval();
        this.pause = cfg.warmPause();
    }

    public void start() {
        Thread t = new Thread(this, "universe-warmer");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                pass();
                Thread.sleep(interval.toMillis());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pass() throws InterruptedException {
        int ok = 0;
        int failed = 0;
        int streak = 0;
        for (InstrumentInfo info : java.util.stream.Stream.concat(
                universe.equities().stream(), universe.fx().stream()).toList()) {
            if (cache.refresh(info.symbol()).isPresent()) {
                ok++;
                streak = 0;
            } else {
                failed++;
                if (++streak >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn("Market data warm-up stopped early after {} failures in a row "
                            + "(loaded {}, failed {}); retrying in {}", streak, ok, failed, interval);
                    return;
                }
            }
            Thread.sleep(pause.toMillis());
        }
        log.info("Market data warm-up finished: {} loaded, {} failed, {} cached in total", ok, failed,
                cache.cachedCount());
    }
}
