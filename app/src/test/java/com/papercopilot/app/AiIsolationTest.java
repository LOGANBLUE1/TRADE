package com.papercopilot.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.papercopilot.app.TestSupport.StubChatModel;

/** The promise: when AI is out of credits or broken, everything that is not AI keeps working. */
@SpringBootTest(properties = "copilot.ai.starting-credits=2")
@AutoConfigureMockMvc
@Import(TestSupport.class)
class AiIsolationTest {

    static final String PE_UNDER_10 = """
            {"conditions":[{"field":"PE","op":"LT","value":10,"otherField":null}],
             "sortBy":"PE","sortDescending":false,"limit":null}""";

    @Autowired MockMvc mvc;
    @Autowired StubChatModel llm;

    String user;

    @BeforeEach
    void setUp() {
        user = "u-" + UUID.randomUUID();
        llm.down = false;
        llm.reply = PE_UNDER_10;
        llm.calls.set(0);
    }

    @Test
    void naturalLanguageScreen_runsTheModelsFilterAgainstMarketData_andChargesOneCredit() throws Exception {
        aiScreen("cheap stocks, pe under 10").andExpect(status().isOk())
                .andExpect(jsonPath("$.result.matches[*].symbol").value(
                        org.hamcrest.Matchers.contains("COALINDIA.NS", "ONGC.NS", "SBIN.NS")))
                .andExpect(jsonPath("$.creditsRemaining").value(1));
    }

    @Test
    void whenCreditsRunOut_aiReturns402_andEveryCoreFeatureStillWorks() throws Exception {
        aiScreen("pe under 10").andExpect(status().isOk());
        aiScreen("pe under 10").andExpect(status().isOk());
        aiScreen("pe under 10").andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("AI_CREDITS_EXHAUSTED"));
        assertThat(llm.calls.get()).isEqualTo(2);   // the third request never reached the model

        mvc.perform(get("/api/me/entitlements").header("X-User-Id", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false))
                .andExpect(jsonPath("$.aiCreditsRemaining").value(0));
        assertCoreWorks();
    }

    @Test
    void whenTheModelIsDown_aiReturns503_noCreditIsCharged_andCoreStillWorks() throws Exception {
        llm.down = true;
        aiScreen("pe under 10").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        mvc.perform(get("/api/me/entitlements").header("X-User-Id", user))
                .andExpect(jsonPath("$.aiCreditsRemaining").value(2));
        assertCoreWorks();
    }

    @Test
    void anInvalidFilterFromTheModel_isRejected_notRun_andNotCharged() throws Exception {
        llm.reply = """
                {"conditions":[{"field":"PE","op":"LT","value":null,"otherField":null}]}""";
        aiScreen("pe under 10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        mvc.perform(get("/api/me/entitlements").header("X-User-Id", user))
                .andExpect(jsonPath("$.aiCreditsRemaining").value(2));
    }

    private void assertCoreWorks() throws Exception {
        mvc.perform(get("/api/quotes/TCS").header("X-User-Id", user)).andExpect(status().isOk());
        mvc.perform(post("/api/screens/run").header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON)
                .content(PE_UNDER_10)).andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(3));
        mvc.perform(post("/api/orders").header("X-User-Id", user).header("Idempotency-Key", "core-buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"TCS\",\"side\":\"BUY\",\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FILLED"));
        mvc.perform(post("/api/orders").header("X-User-Id", user).header("Idempotency-Key", "core-sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"TCS\",\"side\":\"SELL\",\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FILLED"));
        mvc.perform(get("/api/portfolio").header("X-User-Id", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].quantity").value(1));
        mvc.perform(get("/api/orders").header("X-User-Id", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/audit").header("X-User-Id", user)).andExpect(status().isOk());
    }

    private ResultActions aiScreen(String query) throws Exception {
        return mvc.perform(post("/api/ai/screen").header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"" + query + "\"}"));
    }
}
