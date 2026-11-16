package com.papercopilot.core.screener;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.lang.Nullable;

/**
 * The structured screen. This is the contract between the manual filter builder, saved screens,
 * the MCP tool and the LLM's natural-language translation: all of them produce one of these.
 */
public record FilterSpec(List<Condition> conditions, @Nullable Field sortBy, @Nullable Boolean sortDescending,
                         @Nullable Integer limit) {

    public enum Field { PRICE, CHANGE_PCT, PE, MARKET_CAP_CR, VOLUME, AVG_VOLUME_20D, VOLUME_RATIO, SMA_50, SMA_200 }

    public enum Op { LT, LTE, GT, GTE }

    /**
     * Compare a field against either a constant (value) or another field (otherField), never both.
     * Example "price above 50-day average": field=PRICE, op=GT, otherField=SMA_50.
     */
    public record Condition(Field field, Op op, @Nullable BigDecimal value, @Nullable Field otherField) {}
}
