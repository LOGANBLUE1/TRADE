package com.papercopilot.core.marketdata;

/** Static facts about an allowed instrument. type is EQUITY or FX; industry is null for FX. */
public record InstrumentInfo(String symbol, String name, String industry, String type) {

    public boolean tradable() {
        return "EQUITY".equals(type);
    }
}
