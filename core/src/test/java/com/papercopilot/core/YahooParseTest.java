package com.papercopilot.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Parses a hand-built payload in the chart endpoint's shape; not a recording of a live response. */
class YahooParseTest {

    @Test
    void derivesQuoteAndAveragesFromDailyBars() throws Exception {
        StringBuilder closes = new StringBuilder();
        StringBuilder volumes = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            closes.append(i == 30 ? "null" : String.valueOf(100 + i)).append(i < 60 ? "," : "");
            volumes.append(i == 30 ? "null" : String.valueOf(1000 * i)).append(i < 60 ? "," : "");
        }
        String json = """
                {"chart":{"result":[{"meta":{"currency":"INR","symbol":"X.NS","longName":"X Ltd",
                 "regularMarketPrice":160.0,"regularMarketTime":1794800000},
                 "indicators":{"quote":[{"close":[%s],"volume":[%s]}]}}],"error":null}}""".formatted(closes, volumes);

        InstrumentSnapshot s = YahooMarketDataProvider.parse("X.NS", new ObjectMapper().readTree(json)).orElseThrow();

        assertThat(s.price()).isEqualByComparingTo("160.0");
        assertThat(s.quote().previousClose()).isEqualByComparingTo("159");
        assertThat(s.quote().changePct()).isEqualByComparingTo("0.63");
        assertThat(s.quote().delayed()).isTrue();
        assertThat(s.sma50()).isNotNull();
        assertThat(s.sma200()).isNull();                       // under 200 bars of history
        assertThat(s.volume()).isEqualByComparingTo("60000");
        assertThat(s.pe()).isNull();
    }

    @Test
    void unknownSymbolPayloadGivesEmpty() throws Exception {
        String json = """
                {"chart":{"result":null,"error":{"code":"Not Found"}}}""";
        assertThat(YahooMarketDataProvider.parse("NOPE", new ObjectMapper().readTree(json))).isEmpty();
    }
}
