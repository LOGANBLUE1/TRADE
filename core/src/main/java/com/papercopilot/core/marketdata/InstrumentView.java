package com.papercopilot.core.marketdata;

/** type is EQUITY or FX. Only tradable instruments can be bought or sold on the paper account. */
public record InstrumentView(String type, boolean tradable, InstrumentSnapshot data) {}
