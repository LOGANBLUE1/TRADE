package com.papercopilot.core.marketdata;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.papercopilot.core.config.CoreProperties;

/** The allowed-symbols guardrail: nothing outside this list can be quoted, screened or traded. */
@Component
public class SymbolUniverse {

    private final Set<String> equities;
    private final Set<String> fx;

    public SymbolUniverse(CoreProperties props) {
        this.equities = new LinkedHashSet<>(props.universe().equities());
        this.fx = new LinkedHashSet<>(props.universe().fx());
    }

    /** Accepts "reliance", "RELIANCE.NS", "gbpusd" and returns the canonical symbol if it is allowed. */
    public Optional<String> resolve(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String s = input.trim().toUpperCase(Locale.ROOT);
        for (String candidate : List.of(s, s + ".NS", s + ".BO", s + "=X")) {
            if (equities.contains(candidate) || fx.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public boolean isTradable(String canonicalSymbol) {
        return equities.contains(canonicalSymbol);
    }

    public Set<String> equities() {
        return equities;
    }
}
