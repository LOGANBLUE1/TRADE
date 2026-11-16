package com.papercopilot.core.marketdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.papercopilot.core.config.CoreProperties;

/** The allowed-symbols guardrail: nothing outside this list can be quoted, screened or traded. */
@Component
public class SymbolUniverse {

    private static final Map<String, String> FX_NAMES = Map.of(
            "GBPUSD=X", "GBP/USD", "USDINR=X", "USD/INR", "GBPINR=X", "GBP/INR", "EURUSD=X", "EUR/USD");

    private final Map<String, InstrumentInfo> equities = new LinkedHashMap<>();
    private final Map<String, InstrumentInfo> fx = new LinkedHashMap<>();

    public SymbolUniverse(CoreProperties props) {
        loadEquities(props.universe().equitiesFile());
        for (String symbol : props.universe().fx() == null ? List.<String>of() : props.universe().fx()) {
            fx.put(symbol, new InstrumentInfo(symbol, FX_NAMES.getOrDefault(symbol, symbol.replace("=X", "")),
                    null, "FX"));
        }
    }

    /** Rows look like: Reliance Industries Ltd.,Oil Gas & Consumable Fuels,RELIANCE,EQ,INE002A01018 */
    private void loadEquities(String location) {
        Resource resource = new DefaultResourceLoader().getResource(location);
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = in.readLine();   // header
            while ((line = in.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length < 3 || cols[2].isBlank()) {
                    continue;
                }
                String symbol = cols[2].trim().toUpperCase(Locale.ROOT) + ".NS";
                equities.put(symbol, new InstrumentInfo(symbol, cols[0].trim(), cols[1].trim(), "EQUITY"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the equity universe from " + location, e);
        }
        if (equities.isEmpty()) {
            throw new IllegalStateException("The equity universe file has no rows: " + location);
        }
    }

    /** Accepts "reliance", "RELIANCE.NS", "m&m", "gbpusd" and returns the canonical symbol if it is allowed. */
    public Optional<String> resolve(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String s = input.trim().toUpperCase(Locale.ROOT);
        for (String candidate : List.of(s, s + ".NS", s + "=X")) {
            if (equities.containsKey(candidate) || fx.containsKey(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<InstrumentInfo> info(String canonicalSymbol) {
        InstrumentInfo e = equities.get(canonicalSymbol);
        return Optional.ofNullable(e != null ? e : fx.get(canonicalSymbol));
    }

    public boolean isTradable(String canonicalSymbol) {
        return equities.containsKey(canonicalSymbol);
    }

    public Collection<InstrumentInfo> equities() {
        return equities.values();
    }

    public Collection<InstrumentInfo> fx() {
        return fx.values();
    }
}
