package com.papercopilot.app;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSupport.class)
class InstrumentApiTest {

    @Autowired MockMvc mvc;

    @Test
    void listsEveryAllowedInstrument_equitiesTradable_fxQuoteOnly() throws Exception {
        mvc.perform(get("/api/instruments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].type").value("EQUITY"))
                .andExpect(jsonPath("$[0].tradable").value(true))
                .andExpect(jsonPath("$[19].type").value("FX"))
                .andExpect(jsonPath("$[19].tradable").value(false));
    }

    @Test
    void searchMatchesNameOrSymbol_caseInsensitive() throws Exception {
        mvc.perform(get("/api/instruments").param("q", "bank")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].symbol").value(
                        containsInAnyOrder("HDFCBANK.NS", "ICICIBANK.NS", "SBIN.NS", "AXISBANK.NS")));
        mvc.perform(get("/api/instruments").param("q", "gbp"))
                .andExpect(jsonPath("$[*].symbol").value(contains("GBPUSD=X", "GBPINR=X")));
        mvc.perform(get("/api/instruments").param("q", "zzz")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/instruments").param("q", "sb"))          // symbol prefix ranks first
                .andExpect(jsonPath("$[0].symbol").value("SBIN.NS"))
                .andExpect(jsonPath("$[0].name").value("State Bank of India"))
                .andExpect(jsonPath("$[0].industry").value("Financial Services"));
        mvc.perform(get("/api/instruments").param("q", "bank").param("limit", "2"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void detailBySymbol_andUnknownSymbolIsRejected() throws Exception {
        mvc.perform(get("/api/instruments/tcs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("TCS.NS"))
                .andExpect(jsonPath("$.data.pe").value(26.3))
                .andExpect(jsonPath("$.data.quote.source").value("SIMULATED"));
        mvc.perform(get("/api/instruments/NOPE")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYMBOL_NOT_ALLOWED"));
    }
}
