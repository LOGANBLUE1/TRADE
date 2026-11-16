package com.papercopilot.core.screener;

import java.time.Instant;
import java.util.List;

import com.papercopilot.core.marketdata.InstrumentSnapshot;

public record ScreenResult(FilterSpec filter, int universeSize, int unavailable, List<InstrumentSnapshot> matches,
                           Instant asOf) {}
