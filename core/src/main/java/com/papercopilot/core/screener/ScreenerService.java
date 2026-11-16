package com.papercopilot.core.screener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.papercopilot.core.error.DomainException;
import com.papercopilot.core.error.ErrorCode;
import com.papercopilot.core.marketdata.InstrumentSnapshot;
import com.papercopilot.core.marketdata.MarketDataProvider;
import com.papercopilot.core.marketdata.SymbolUniverse;
import com.papercopilot.core.screener.FilterSpec.Condition;
import com.papercopilot.core.screener.FilterSpec.Field;

@Service
public class ScreenerService {

    private static final int MAX_CONDITIONS = 12;
    private static final int MAX_LIMIT = 100;

    private final MarketDataProvider provider;
    private final SymbolUniverse universe;
    private final Clock clock;

    public ScreenerService(MarketDataProvider provider, SymbolUniverse universe, Clock clock) {
        this.provider = provider;
        this.universe = universe;
        this.clock = clock;
    }

    public ScreenResult run(FilterSpec spec) {
        validate(spec);
        List<InstrumentSnapshot> matches = new ArrayList<>();
        int unavailable = 0;
        for (String symbol : universe.equities()) {
            Optional<InstrumentSnapshot> snap = provider.snapshot(symbol);
            if (snap.isEmpty()) {
                unavailable++;
            } else if (spec.conditions().stream().allMatch(c -> matches(snap.get(), c))) {
                matches.add(snap.get());
            }
        }
        if (spec.sortBy() != null) {
            Comparator<InstrumentSnapshot> cmp = Comparator.comparing(
                    s -> value(s, spec.sortBy()), Comparator.nullsFirst(Comparator.naturalOrder()));
            matches.sort(Boolean.FALSE.equals(spec.sortDescending()) ? cmp : cmp.reversed());
        }
        int limit = spec.limit() == null ? MAX_LIMIT : spec.limit();
        return new ScreenResult(spec, universe.equities().size(), unavailable,
                List.copyOf(matches.subList(0, Math.min(limit, matches.size()))), clock.instant());
    }

    /** Also used to vet LLM output before it is run: the model's JSON is untrusted. */
    public void validate(FilterSpec spec) {
        if (spec == null || spec.conditions() == null) {
            throw invalid("A filter needs a 'conditions' list (it may be empty).");
        }
        if (spec.conditions().size() > MAX_CONDITIONS) {
            throw invalid("At most " + MAX_CONDITIONS + " conditions are allowed.");
        }
        if (spec.limit() != null && (spec.limit() < 1 || spec.limit() > MAX_LIMIT)) {
            throw invalid("limit must be between 1 and " + MAX_LIMIT + ".");
        }
        for (Condition c : spec.conditions()) {
            if (c == null || c.field() == null || c.op() == null) {
                throw invalid("Every condition needs a field and an op.");
            }
            if ((c.value() == null) == (c.otherField() == null)) {
                throw invalid("Condition on " + c.field() + " needs exactly one of 'value' or 'otherField'.");
            }
        }
    }

    /** A condition on data the provider does not have (null) never matches. */
    private static boolean matches(InstrumentSnapshot s, Condition c) {
        BigDecimal left = value(s, c.field());
        BigDecimal right = c.value() != null ? c.value() : value(s, c.otherField());
        if (left == null || right == null) {
            return false;
        }
        int cmp = left.compareTo(right);
        return switch (c.op()) {
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
        };
    }

    private static BigDecimal value(InstrumentSnapshot s, Field f) {
        return switch (f) {
            case PRICE -> s.price();
            case CHANGE_PCT -> s.changePct();
            case PE -> s.pe();
            case MARKET_CAP_CR -> s.marketCapCr();
            case VOLUME -> s.volume();
            case AVG_VOLUME_20D -> s.avgVolume20d();
            case VOLUME_RATIO -> s.volume() == null || s.avgVolume20d() == null || s.avgVolume20d().signum() == 0
                    ? null : s.volume().divide(s.avgVolume20d(), 4, RoundingMode.HALF_UP);
            case SMA_50 -> s.sma50();
            case SMA_200 -> s.sma200();
        };
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.INVALID_FILTER, message);
    }
}
