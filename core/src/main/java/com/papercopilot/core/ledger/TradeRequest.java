package com.papercopilot.core.ledger;

public record TradeRequest(String symbol, OrderSide side, long quantity, String rationale) {}
