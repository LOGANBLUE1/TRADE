package com.papercopilot.core.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Made-up numbers for local development and tests. These are NOT real market prices; every quote is
 * labelled source=SIMULATED so the UI can say so. A few symbols have hand-written rows; every other
 * symbol in the universe gets stable pseudo-random values derived from its symbol.
 */
public class SimulatedMarketDataProvider implements MarketDataProvider {

    private record Row(String name, String currency, double price, double prevClose, Double pe,
                       Double mcapCr, Double volume, Double avgVolume, Double sma50, Double sma200) {}

    private static final Map<String, Row> DATA = new LinkedHashMap<>();

    static {
        eq("RELIANCE.NS", "Reliance Industries", 1480.50, 1468.00, 24.1, 2003000, 9.8e6, 8.1e6, 1442.0, 1390.0);
        eq("TCS.NS", "Tata Consultancy Services", 3525.00, 3560.40, 26.3, 1275000, 2.1e6, 2.4e6, 3590.0, 3760.0);
        eq("INFY.NS", "Infosys", 1612.30, 1601.10, 23.8, 669000, 6.4e6, 5.2e6, 1575.0, 1640.0);
        eq("HDFCBANK.NS", "HDFC Bank", 1965.75, 1958.20, 19.6, 1505000, 7.9e6, 8.8e6, 1930.0, 1815.0);
        eq("ICICIBANK.NS", "ICICI Bank", 1422.10, 1410.00, 18.9, 1014000, 11.2e6, 9.0e6, 1398.0, 1322.0);
        eq("SBIN.NS", "State Bank of India", 812.40, 804.90, 9.4, 725000, 14.6e6, 10.9e6, 795.0, 788.0);
        eq("ITC.NS", "ITC", 418.20, 421.00, 25.7, 523000, 9.1e6, 10.2e6, 425.0, 436.0);
        eq("LT.NS", "Larsen & Toubro", 3655.00, 3622.50, 33.2, 502000, 1.9e6, 1.6e6, 3580.0, 3490.0);
        eq("BHARTIARTL.NS", "Bharti Airtel", 1890.60, 1902.30, 41.5, 1133000, 4.2e6, 4.6e6, 1868.0, 1722.0);
        eq("HINDUNILVR.NS", "Hindustan Unilever", 2385.00, 2379.40, 52.8, 560000, 1.3e6, 1.5e6, 2410.0, 2455.0);
        eq("TATAMOTORS.NS", "Tata Motors", 688.90, 676.30, 11.2, 253000, 13.5e6, 9.7e6, 671.0, 742.0);
        eq("SUNPHARMA.NS", "Sun Pharmaceutical", 1702.40, 1695.00, 35.9, 408000, 2.2e6, 2.3e6, 1688.0, 1731.0);
        eq("MARUTI.NS", "Maruti Suzuki", 12640.00, 12510.00, 27.4, 397000, 0.52e6, 0.41e6, 12380.0, 12010.0);
        eq("AXISBANK.NS", "Axis Bank", 1178.30, 1185.60, 13.1, 365000, 8.3e6, 8.0e6, 1192.0, 1140.0);
        eq("COALINDIA.NS", "Coal India", 392.15, 388.00, 7.2, 241000, 8.8e6, 6.3e6, 385.0, 401.0);
        eq("ONGC.NS", "Oil & Natural Gas Corp", 243.80, 241.10, 8.3, 306000, 12.1e6, 9.4e6, 240.0, 251.0);
        fx("GBPUSD=X", "GBP/USD", "USD", 1.3120, 1.3095);
        fx("USDINR=X", "USD/INR", "INR", 88.42, 88.35);
        fx("GBPINR=X", "GBP/INR", "INR", 116.01, 115.70);
        fx("EURUSD=X", "EUR/USD", "USD", 1.1580, 1.1602);
    }

    private static void eq(String symbol, String name, double price, double prev, double pe, double mcapCr,
                           double volume, double avgVolume, double sma50, double sma200) {
        DATA.put(symbol, new Row(name, "INR", price, prev, pe, mcapCr, volume, avgVolume, sma50, sma200));
    }

    private static void fx(String symbol, String name, String currency, double price, double prev) {
        DATA.put(symbol, new Row(name, currency, price, prev, null, null, null, null, null, null));
    }

    private final Clock clock;
    private final SymbolUniverse universe;

    public SimulatedMarketDataProvider(Clock clock, SymbolUniverse universe) {
        this.clock = clock;
        this.universe = universe;
    }

    @Override
    public Optional<InstrumentSnapshot> snapshot(String symbol) {
        Optional<InstrumentInfo> info = universe.info(symbol);
        if (info.isEmpty()) {
            return Optional.empty();
        }
        Row r = DATA.get(symbol);
        if (r == null) {
            if (!info.get().tradable()) {
                return Optional.empty();
            }
            r = generate(symbol, info.get().name());
        }
        BigDecimal price = BigDecimal.valueOf(r.price);
        BigDecimal prev = BigDecimal.valueOf(r.prevClose);
        BigDecimal changePct = price.subtract(prev).multiply(BigDecimal.valueOf(100))
                .divide(prev, 2, RoundingMode.HALF_UP);
        String name = info.get().name();
        Quote quote = new Quote(symbol, name, price, prev, changePct, r.currency, clock.instant(), false, "SIMULATED");
        return Optional.of(new InstrumentSnapshot(symbol, name, price, changePct, bd(r.pe), bd(r.mcapCr),
                bd(r.volume), bd(r.avgVolume), bd(r.sma50), bd(r.sma200), quote));
    }

    /** Same symbol, same numbers, on every run. */
    private static Row generate(String symbol, String name) {
        Random rnd = new Random(symbol.hashCode() * 2654435761L);
        double price = round2(40 * Math.pow(150, rnd.nextDouble()));            // 40 .. 6000, skewed low
        double prev = round2(price / (1 + (rnd.nextDouble() - 0.48) * 0.06));   // -2.9% .. +3.1% today
        double pe = Math.round((6 + 70 * Math.pow(rnd.nextDouble(), 2)) * 10) / 10.0;
        double mcapCr = Math.round(4000 * Math.pow(120, rnd.nextDouble()));
        double avgVolume = Math.round(50_000 * Math.pow(200, rnd.nextDouble()));
        double volume = Math.round(avgVolume * (0.5 + rnd.nextDouble() * 1.2));
        double sma50 = round2(price * (0.9 + rnd.nextDouble() * 0.2));
        double sma200 = round2(price * (0.8 + rnd.nextDouble() * 0.4));
        return new Row(name, "INR", price, prev, pe, mcapCr, volume, avgVolume, sma50, sma200);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static BigDecimal bd(Double d) {
        return d == null ? null : BigDecimal.valueOf(d);
    }
}
