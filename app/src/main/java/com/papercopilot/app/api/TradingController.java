package com.papercopilot.app.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.papercopilot.core.ledger.AuditView;
import com.papercopilot.core.ledger.OrderService;
import com.papercopilot.core.ledger.OrderStatus;
import com.papercopilot.core.ledger.OrderView;
import com.papercopilot.core.ledger.PortfolioService;
import com.papercopilot.core.ledger.PortfolioView;
import com.papercopilot.core.ledger.TradeRequest;

/**
 * The user's own trading API. X-User-Id is a development stand-in for real authentication and must be
 * replaced (Spring Security + JWT/session) before this is reachable by anyone else.
 */
@RestController
@RequestMapping("/api")
public class TradingController {

    private static final String USER = "X-User-Id";

    private final OrderService orders;
    private final PortfolioService portfolio;

    public TradingController(OrderService orders, PortfolioService portfolio) {
        this.orders = orders;
        this.portfolio = portfolio;
    }

    @GetMapping("/portfolio")
    public PortfolioView portfolio(@RequestHeader(value = USER, defaultValue = "demo") String userId) {
        return portfolio.get(userId);
    }

    @GetMapping("/audit")
    public List<AuditView> audit(@RequestHeader(value = USER, defaultValue = "demo") String userId) {
        return portfolio.auditTrail(userId);
    }

    /** ?status=PENDING_APPROVAL&status=APPROVED gives the active orders. */
    @GetMapping("/orders")
    public List<OrderView> list(@RequestHeader(value = USER, defaultValue = "demo") String userId,
                                @RequestParam(required = false) List<OrderStatus> status) {
        return orders.list(userId, status);
    }

    @GetMapping("/orders/{id}")
    public OrderView get(@RequestHeader(value = USER, defaultValue = "demo") String userId, @PathVariable UUID id) {
        return orders.get(userId, id);
    }

    /** Manual buy/sell: placed and filled immediately, since the user is the one clicking. */
    @PostMapping("/orders")
    public OrderView place(@RequestHeader(value = USER, defaultValue = "demo") String userId,
                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                           @RequestBody TradeRequest request) {
        return orders.placeManual(userId, request, idempotencyKey);
    }

    @PostMapping("/orders/{id}/approve")
    public OrderView approve(@RequestHeader(value = USER, defaultValue = "demo") String userId,
                             @PathVariable UUID id) {
        return orders.approve(userId, id);
    }

    @PostMapping("/orders/{id}/reject")
    public OrderView reject(@RequestHeader(value = USER, defaultValue = "demo") String userId,
                            @PathVariable UUID id) {
        return orders.reject(userId, id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderView cancel(@RequestHeader(value = USER, defaultValue = "demo") String userId,
                            @PathVariable UUID id) {
        return orders.cancel(userId, id);
    }

    @PostMapping("/orders/{id}/execute")
    public OrderView execute(@RequestHeader(value = USER, defaultValue = "demo") String userId,
                             @PathVariable UUID id) {
        return orders.execute(userId, id, "user:" + userId);
    }
}
