package com.papercopilot.core.marketdata;

/**
 * type is EQUITY or FX. Only tradable instruments can be bought or sold on the paper account.
 * data is null when no price data is loaded for the instrument at the moment.
 */
public record InstrumentView(String symbol, String name, String industry, String type, boolean tradable,
                             InstrumentSnapshot data) {

    static InstrumentView of(InstrumentInfo info, InstrumentSnapshot data) {
        return new InstrumentView(info.symbol(), info.name(), info.industry(), info.type(), info.tradable(), data);
    }
}
