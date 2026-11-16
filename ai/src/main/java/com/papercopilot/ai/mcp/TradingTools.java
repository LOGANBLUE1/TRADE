package com.papercopilot.ai.mcp;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.papercopilot.ai.AiProperties;
import com.papercopilot.core.error.DomainException;
import com.papercopilot.core.error.ErrorCode;
import com.papercopilot.core.ledger.OrderService;
import com.papercopilot.core.ledger.OrderSide;
import com.papercopilot.core.ledger.OrderSource;
import com.papercopilot.core.ledger.OrderView;
import com.papercopilot.core.ledger.PortfolioService;
import com.papercopilot.core.ledger.PortfolioView;
import com.papercopilot.core.ledger.TradeRequest;
import com.papercopilot.core.marketdata.Quote;
import com.papercopilot.core.marketdata.QuoteService;
import com.papercopilot.core.screener.FilterSpec;
import com.papercopilot.core.screener.ScreenResult;
import com.papercopilot.core.screener.ScreenerService;

/**
 * The MCP tool surface. Thin adapters over core services; there is deliberately no tool that approves
 * an order. Approval happens only through the user-facing REST API.
 */
@Component
public class TradingTools {

    private final QuoteService quotes;
    private final ScreenerService screener;
    private final PortfolioService portfolio;
    private final OrderService orders;
    private final String userId;

    public TradingTools(QuoteService quotes, ScreenerService screener, PortfolioService portfolio,
                        OrderService orders, AiProperties props) {
        this.quotes = quotes;
        this.screener = screener;
        this.portfolio = portfolio;
        this.orders = orders;
        this.userId = props.mcpUserId();
    }

    @Tool(name = "get_quote", resultConverter = ToolJson.class, description = """
            Get the latest available price for one allowed symbol (NSE equities such as RELIANCE.NS, or FX \
            pairs such as GBPUSD=X). Always call this instead of recalling a price. Check 'delayed', 'asOf' \
            and 'source' before describing the price as current.""")
    public Quote getQuote(@ToolParam(description = "Symbol, e.g. RELIANCE.NS, TCS, GBPUSD=X") String symbol) {
        return call(() -> quotes.getQuote(symbol));
    }

    @Tool(name = "screen_stocks", resultConverter = ToolJson.class, description = """
            Run a structured screen over the allowed equity universe. Every condition compares a field with \
            either a constant ('value') or another field ('otherField'), never both. Fields: PRICE, CHANGE_PCT, \
            PE, MARKET_CAP_CR, VOLUME, AVG_VOLUME_20D, VOLUME_RATIO, SMA_50, SMA_200. Ops: LT, LTE, GT, GTE. \
            A stock with no data for a field never matches a condition on it.""")
    public ScreenResult screenStocks(@ToolParam(description = "The filter to run") FilterSpec filter) {
        return call(() -> screener.run(filter));
    }

    @Tool(name = "get_portfolio", description = "Get the paper-trading account: cash, positions, valuations.",
            resultConverter = ToolJson.class)
    public PortfolioView getPortfolio() {
        return call(() -> portfolio.get(userId));
    }

    @Tool(name = "propose_trade", resultConverter = ToolJson.class, description = """
            Propose a paper trade. This does NOT trade: it creates an order in PENDING_APPROVAL that the user \
            must approve in the app. Tell the user the order id and that it is waiting for their approval. \
            Reuse the same idempotencyKey when retrying the same proposal.""")
    public OrderView proposeTrade(
            @ToolParam(description = "Tradable equity symbol, e.g. INFY.NS") String symbol,
            @ToolParam(description = "BUY or SELL") String side,
            @ToolParam(description = "Number of shares, a positive whole number") long quantity,
            @ToolParam(description = "Short reason shown to the user when they review the order", required = false)
            String rationale,
            @ToolParam(description = "Unique key for this proposal, e.g. a UUID") String idempotencyKey) {
        return call(() -> {
            OrderSide parsed;
            try {
                parsed = OrderSide.valueOf(String.valueOf(side).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomainException(ErrorCode.INVALID_REQUEST, "side must be BUY or SELL.");
            }
            return orders.propose(userId, new TradeRequest(symbol, parsed, quantity, rationale), OrderSource.AI,
                    idempotencyKey);
        });
    }

    @Tool(name = "get_order", description = "Get an order's current status, e.g. to see whether it was approved.",
            resultConverter = ToolJson.class)
    public OrderView getOrder(@ToolParam(description = "Order id returned by propose_trade") String orderId) {
        return call(() -> orders.get(userId, parseId(orderId)));
    }

    @Tool(name = "execute_trade", resultConverter = ToolJson.class, description = """
            Execute an order the user has already approved. Fails with APPROVAL_REQUIRED if it is still \
            pending; in that case ask the user to approve it in the app, do not retry in a loop.""")
    public OrderView executeTrade(@ToolParam(description = "Order id returned by propose_trade") String orderId) {
        return call(() -> orders.execute(userId, parseId(orderId), "ai-for:" + userId));
    }

    /** Puts the error code in the text the model sees, so it can tell APPROVAL_REQUIRED from a real failure. */
    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            throw new ToolFailure(e);
        }
    }

    public static class ToolFailure extends RuntimeException {

        private final ErrorCode code;

        ToolFailure(DomainException cause) {
            super(cause.getCode() + ": " + cause.getMessage(), cause);
            this.code = cause.getCode();
        }

        public ErrorCode getCode() {
            return code;
        }
    }

    private static UUID parseId(String orderId) {
        try {
            return UUID.fromString(String.valueOf(orderId).trim());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.ORDER_NOT_FOUND, "Not a valid order id: " + orderId);
        }
    }
}
