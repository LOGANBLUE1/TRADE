package com.papercopilot.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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

import com.jayway.jsonpath.JsonPath;
import com.papercopilot.ai.mcp.TradingTools;
import com.papercopilot.app.TestSupport.MutableClock;
import com.papercopilot.app.TestSupport.MutableMarketData;
import com.papercopilot.core.error.ErrorCode;
import com.papercopilot.core.ledger.OrderService;
import com.papercopilot.core.ledger.OrderSide;
import com.papercopilot.core.ledger.OrderSource;
import com.papercopilot.core.ledger.OrderStatus;
import com.papercopilot.core.ledger.OrderView;
import com.papercopilot.core.ledger.TradeRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSupport.class)
class TradingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired OrderService orders;
    @Autowired TradingTools tools;
    @Autowired MutableClock clock;
    @Autowired MutableMarketData market;

    String user;

    @BeforeEach
    void freshUser() {
        user = "u-" + UUID.randomUUID();
        clock.reset();
        market.reset();
    }

    @Test
    void manualBuyThenSell_movesCashAndPosition_andReplayDoesNotDoubleFill() throws Exception {
        place("k1", "INFY", "BUY", 10).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.symbol").value("INFY.NS"));
        place("k1", "INFY", "BUY", 10).andExpect(status().isOk());   // retry with the same key

        mvc.perform(get("/api/portfolio").header("X-User-Id", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(1_000_000 - 16_123.0))
                .andExpect(jsonPath("$.positions[0].quantity").value(10));

        place("k2", "INFY.NS", "SELL", 4).andExpect(jsonPath("$.status").value("FILLED"));
        mvc.perform(get("/api/portfolio").header("X-User-Id", user))
                .andExpect(jsonPath("$.positions[0].quantity").value(6))
                .andExpect(jsonPath("$.totalEquity").value(1_000_000.0));
    }

    @Test
    void sameKeyForADifferentOrder_isRejected() throws Exception {
        place("k1", "INFY", "BUY", 10).andExpect(status().isOk());
        place("k1", "TCS", "BUY", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void aiProposedOrder_executesOnlyAfterTheUserApproves_andOnlyOnce() throws Exception {
        // The MCP tools act on the configured MCP user ("demo").
        OrderView proposed = tools.proposeTrade("SBIN", "buy", 5, "test", "ai-" + UUID.randomUUID());
        assertThat(proposed.status()).isEqualTo(OrderStatus.PENDING_APPROVAL);
        String id = proposed.id().toString();

        assertThatThrownBy(() -> tools.executeTrade(id)).hasMessageStartingWith("APPROVAL_REQUIRED");
        assertThat(tools.getOrder(id).status()).isEqualTo(OrderStatus.PENDING_APPROVAL);

        double cashBefore = cash("demo");
        mvc.perform(post("/api/orders/" + id + "/approve")).andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedBy").value("user:demo"));
        assertThat(tools.executeTrade(id).status()).isEqualTo(OrderStatus.FILLED);
        assertThat(tools.executeTrade(id).status()).isEqualTo(OrderStatus.FILLED);   // retry is a no-op
        assertThat(cash("demo")).isEqualTo(cashBefore - 5 * 812.40);

        mvc.perform(get("/api/audit")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId=='" + id + "')].eventType").value(
                        org.hamcrest.Matchers.contains("FILLED", "APPROVED", "PROPOSED")));
    }

    @Test
    void rejectedOrder_cannotBeExecuted() throws Exception {
        OrderView o = propose("ITC", OrderSide.BUY, 10);
        mvc.perform(post("/api/orders/" + o.id() + "/reject").header("X-User-Id", user))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        execute(o).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void approvalExpires() throws Exception {
        OrderView o = propose("ITC", OrderSide.BUY, 10);
        orders.approve(user, o.id());
        clock.advance(Duration.ofMinutes(16));
        execute(o).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_EXPIRED"));
        mvc.perform(get("/api/orders/" + o.id()).header("X-User-Id", user))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(cash(user)).isEqualTo(1_000_000.0);
    }

    @Test
    void priceMoveSinceProposal_blocksTheFill() throws Exception {
        OrderView o = propose("ITC", OrderSide.BUY, 10);       // reference 418.20
        orders.approve(user, o.id());
        market.setPrice("ITC.NS", "440.00");                    // +5.2%, limit is 2%
        execute(o).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_MOVED"));
        assertThat(cash(user)).isEqualTo(1_000_000.0);
    }

    @Test
    void limitsAreEnforced() throws Exception {
        place("a", "MARUTI", "BUY", 100).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_TOO_LARGE"));
        place("b", "ITC", "BUY", 10_001).andExpect(jsonPath("$.code").value("ORDER_TOO_LARGE"));
        place("c", "NOTASTOCK", "BUY", 1).andExpect(jsonPath("$.code").value("SYMBOL_NOT_ALLOWED"));
        place("d", "GBPUSD=X", "BUY", 1).andExpect(jsonPath("$.code").value("SYMBOL_NOT_ALLOWED"));
        place("e", "ITC", "SELL", 1).andExpect(jsonPath("$.code").value("INSUFFICIENT_HOLDINGS"));
        place("f", "ITC", "BUY", 0).andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            place("big" + i, "RELIANCE", "BUY", 130).andExpect(status().isOk());   // 192,465 each
        }
        place("big5", "RELIANCE", "BUY", 130).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        // Failed manual orders leave no trace.
        mvc.perform(get("/api/orders").header("X-User-Id", user)).andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void dailyOrderLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            place("d" + i, "ONGC", "BUY", 1).andExpect(status().isOk());
        }
        place("d20", "ONGC", "BUY", 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_REACHED"));
    }

    @Test
    void ordersAreInvisibleToOtherUsers() throws Exception {
        OrderView o = propose("ITC", OrderSide.BUY, 10);
        mvc.perform(post("/api/orders/" + o.id() + "/approve").header("X-User-Id", "someone-else"))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> tools.executeTrade(o.id().toString())).hasMessageStartingWith(
                ErrorCode.ORDER_NOT_FOUND.name());
    }

    @Test
    void activeOrdersFilter() throws Exception {
        propose("ITC", OrderSide.BUY, 10);
        place("m", "ITC", "BUY", 1);
        mvc.perform(get("/api/orders?status=PENDING_APPROVAL&status=APPROVED").header("X-User-Id", user))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    private OrderView propose(String symbol, OrderSide side, long qty) {
        return orders.propose(user, new TradeRequest(symbol, side, qty, null), OrderSource.AI,
                UUID.randomUUID().toString());
    }

    private ResultActions execute(OrderView o) throws Exception {
        return mvc.perform(post("/api/orders/" + o.id() + "/execute").header("X-User-Id", user));
    }

    private ResultActions place(String key, String symbol, String side, long qty) throws Exception {
        return mvc.perform(post("/api/orders").header("X-User-Id", user).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"%s\",\"side\":\"%s\",\"quantity\":%d}".formatted(symbol, side, qty)));
    }

    private double cash(String userId) throws Exception {
        String body = mvc.perform(get("/api/portfolio").header("X-User-Id", userId)).andReturn().getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.cash")).doubleValue();
    }
}
