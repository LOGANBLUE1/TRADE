package com.papercopilot.core.screener;

import java.time.Instant;
import java.util.List;

import com.papercopilot.core.marketdata.InstrumentSnapshot;

/**
 * totalMatches counts every stock that passed; matches holds at most the filter's limit of them.
 * unavailable counts stocks with no data loaded at the moment, which therefore could not be checked.
 */
public record ScreenResult(FilterSpec filter, int universeSize, int unavailable, int totalMatches,
                           List<InstrumentSnapshot> matches, Instant asOf) {}
