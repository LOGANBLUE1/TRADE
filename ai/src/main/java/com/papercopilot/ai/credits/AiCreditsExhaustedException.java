package com.papercopilot.ai.credits;

public class AiCreditsExhaustedException extends RuntimeException {

    public AiCreditsExhaustedException() {
        super("AI credits are used up. Screening with manual filters, quotes, portfolio and trading still work.");
    }
}
