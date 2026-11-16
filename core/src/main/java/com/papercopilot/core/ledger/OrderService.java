package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.papercopilot.core.config.CoreProperties;
import com.papercopilot.core.error.DomainException;
import com.papercopilot.core.error.ErrorCode;
import com.papercopilot.core.marketdata.MarketDataProvider;
import com.papercopilot.core.marketdata.Quote;
import com.papercopilot.core.marketdata.SymbolUniverse;

/**
 * The order state machine: PENDING_APPROVAL -> APPROVED -> FILLED, with REJECTED / CANCELLED / EXPIRED
 * as dead ends. Every guardrail lives here, so it holds no matter which adapter (REST, MCP) calls in.
 *
 * approve() and placeManual() are the human's actions. The AI module must never call them; an
 * architecture test enforces that.
 */
@Service
public class OrderService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<OrderStatus> NOT_COUNTED =
            List.of(OrderStatus.REJECTED, OrderStatus.CANCELLED, OrderStatus.EXPIRED);
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_RATIONALE_LENGTH = 1000;

    private final AccountService accountService;
    private final AccountRepository accounts;
    private final TradeOrderRepository orders;
    private final PositionRepository positions;
    private final AuditEventRepository audit;
    private final MarketDataProvider marketData;
    private final SymbolUniverse universe;
    private final CoreProperties.Trading limits;
    private final Clock clock;

    OrderService(AccountService accountService, AccountRepository accounts, TradeOrderRepository orders,
                 PositionRepository positions, AuditEventRepository audit, MarketDataProvider marketData,
                 SymbolUniverse universe, CoreProperties props, Clock clock) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.orders = orders;
        this.positions = positions;
        this.audit = audit;
        this.marketData = marketData;
        this.universe = universe;
        this.limits = props.trading();
        this.clock = clock;
    }

    /** Creates a pending order. Replaying the same idempotency key returns the original order. */
    @Transactional
    public OrderView propose(String userId, TradeRequest req, OrderSource source, String idempotencyKey) {
        Account account = accountService.lock(userId);
        return OrderView.of(proposeLocked(account, req, source, idempotencyKey, actorFor(source, userId)));
    }

    /** The human approval. Only reachable from the user-facing REST API. */
    @Transactional
    public OrderView approve(String userId, UUID orderId) {
        Account account = accountService.lock(userId);
        TradeOrder order = load(account, orderId);
        if (order.getStatus() != OrderStatus.APPROVED) {
            approveLocked(account, order, "user:" + userId);
        }
        return OrderView.of(order);
    }

    @Transactional
    public OrderView reject(String userId, UUID orderId) {
        return close(userId, orderId, OrderStatus.REJECTED, "REJECTED", List.of(OrderStatus.PENDING_APPROVAL));
    }

    @Transactional
    public OrderView cancel(String userId, UUID orderId) {
        return close(userId, orderId, OrderStatus.CANCELLED, "CANCELLED",
                List.of(OrderStatus.PENDING_APPROVAL, OrderStatus.APPROVED));
    }

    /**
     * Fills an approved order at the current quote. Executing an already filled order returns it
     * unchanged, so retries never double-fill.
     *
     * noRollbackFor: an expired approval is recorded (status EXPIRED + audit) and then reported as an
     * error. All other failures are thrown before anything is modified.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public OrderView execute(String userId, UUID orderId, String actor) {
        Account account = accountService.lock(userId);
        TradeOrder order = load(account, orderId);
        if (order.getStatus() != OrderStatus.FILLED) {
            executeLocked(account, order, actor);
        }
        return OrderView.of(order);
    }

    /** A user-placed order: proposal, approval and fill in one transaction; any failure leaves nothing behind. */
    @Transactional
    public OrderView placeManual(String userId, TradeRequest req, String idempotencyKey) {
        Account account = accountService.lock(userId);
        String actor = "user:" + userId;
        TradeOrder order = proposeLocked(account, req, OrderSource.MANUAL, idempotencyKey, actor);
        if (order.getSource() != OrderSource.MANUAL) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key already used by another order.");
        }
        if (order.getStatus() == OrderStatus.PENDING_APPROVAL) {
            approveLocked(account, order, actor);
            executeLocked(account, order, actor);
        }
        return OrderView.of(order);
    }

    @Transactional
    public OrderView get(String userId, UUID orderId) {
        return OrderView.of(load(accountService.lock(userId), orderId));
    }

    @Transactional
    public List<OrderView> list(String userId, Collection<OrderStatus> statuses) {
        Account account = accountService.lock(userId);
        List<TradeOrder> found = statuses == null || statuses.isEmpty()
                ? orders.findTop200ByAccountIdOrderByCreatedAtDesc(account.getId())
                : orders.findTop200ByAccountIdAndStatusInOrderByCreatedAtDesc(account.getId(), statuses);
        return found.stream().map(OrderView::of).toList();
    }

    private TradeOrder proposeLocked(Account account, TradeRequest req, OrderSource source, String key,
                                     String actor) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new DomainException(ErrorCode.INVALID_REQUEST,
                    "An idempotency key of 1-" + MAX_KEY_LENGTH + " characters is required.");
        }
        if (req == null || req.side() == null) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "side must be BUY or SELL.");
        }
        String symbol = universe.resolve(req.symbol()).filter(universe::isTradable)
                .orElseThrow(() -> new DomainException(ErrorCode.SYMBOL_NOT_ALLOWED,
                        "Symbol is not tradable here: " + req.symbol()));

        Optional<TradeOrder> existing = orders.findByAccountIdAndIdempotencyKey(account.getId(), key);
        if (existing.isPresent()) {
            TradeOrder o = existing.get();
            if (!o.getSymbol().equals(symbol) || o.getSide() != req.side() || o.getQuantity() != req.quantity()) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "Idempotency key was already used for a different order.");
            }
            return o;
        }

        if (req.quantity() <= 0) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "quantity must be positive.");
        }
        if (req.quantity() > limits.maxOrderQuantity()) {
            throw new DomainException(ErrorCode.ORDER_TOO_LARGE,
                    "quantity exceeds the limit of " + limits.maxOrderQuantity() + ".");
        }
        Quote quote = quote(symbol);
        if (!account.getCurrency().equals(quote.currency())) {
            throw new DomainException(ErrorCode.SYMBOL_NOT_ALLOWED,
                    symbol + " is priced in " + quote.currency() + "; only " + account.getCurrency()
                            + " instruments are tradable.");
        }
        BigDecimal value = quote.price().multiply(BigDecimal.valueOf(req.quantity()));
        if (value.compareTo(limits.maxOrderValue()) > 0) {
            throw new DomainException(ErrorCode.ORDER_TOO_LARGE,
                    "Order value " + value.setScale(2, RoundingMode.HALF_UP) + " exceeds the limit of "
                            + limits.maxOrderValue() + ".");
        }
        Instant now = clock.instant();
        Instant startOfDay = now.atZone(MARKET_ZONE).toLocalDate().atStartOfDay(MARKET_ZONE).toInstant();
        long today = orders.countByAccountIdAndCreatedAtGreaterThanEqualAndStatusNotIn(
                account.getId(), startOfDay, NOT_COUNTED);
        if (today >= limits.maxDailyOrders()) {
            throw new DomainException(ErrorCode.DAILY_LIMIT_REACHED,
                    "Daily limit of " + limits.maxDailyOrders() + " orders reached.");
        }

        String rationale = req.rationale() == null ? null
                : req.rationale().substring(0, Math.min(req.rationale().length(), MAX_RATIONALE_LENGTH));
        TradeOrder order = orders.save(new TradeOrder(account.getId(), symbol, req.side(), req.quantity(), source,
                key, quote.price(), rationale, now));
        record(account, order, "PROPOSED", actor,
                req.side() + " " + req.quantity() + " " + symbol + " @ ref " + quote.price());
        return order;
    }

    private void approveLocked(Account account, TradeOrder order, String actor) {
        if (order.getStatus() != OrderStatus.PENDING_APPROVAL) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "Order is " + order.getStatus() + " and cannot be approved.");
        }
        order.approve(actor, clock.instant());
        record(account, order, "APPROVED", actor, null);
    }

    private void executeLocked(Account account, TradeOrder order, String actor) {
        if (order.getStatus() == OrderStatus.PENDING_APPROVAL) {
            throw new DomainException(ErrorCode.APPROVAL_REQUIRED,
                    "Order " + order.getId() + " has not been approved by the user yet.");
        }
        if (order.getStatus() != OrderStatus.APPROVED) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "Order is " + order.getStatus() + " and cannot be executed.");
        }
        Instant now = clock.instant();
        if (order.getApprovedAt().plus(limits.approvalTtl()).isBefore(now)) {
            order.setStatus(OrderStatus.EXPIRED);
            record(account, order, "EXPIRED", "system", "Approval older than " + limits.approvalTtl());
            throw new DomainException(ErrorCode.APPROVAL_EXPIRED,
                    "The approval expired; propose the trade again.");
        }

        BigDecimal price = quote(order.getSymbol()).price();
        BigDecimal driftPct = price.subtract(order.getReferencePrice()).abs().multiply(BigDecimal.valueOf(100))
                .divide(order.getReferencePrice(), 4, RoundingMode.HALF_UP);
        if (driftPct.compareTo(limits.maxSlippagePct()) > 0) {
            throw new DomainException(ErrorCode.PRICE_MOVED,
                    "Price moved " + driftPct.setScale(2, RoundingMode.HALF_UP) + "% since the order was proposed ("
                            + order.getReferencePrice() + " -> " + price + "). Cancel it and propose again.");
        }

        BigDecimal qty = BigDecimal.valueOf(order.getQuantity());
        BigDecimal value = price.multiply(qty);
        Optional<Position> held = positions.findByAccountIdAndSymbol(account.getId(), order.getSymbol());
        if (order.getSide() == OrderSide.BUY) {
            if (account.getCash().compareTo(value) < 0) {
                throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS,
                        "Needs " + value.setScale(2, RoundingMode.HALF_UP) + " but only "
                                + account.getCash().setScale(2, RoundingMode.HALF_UP) + " cash is available.");
            }
            Position p = held.orElseGet(() -> new Position(account.getId(), order.getSymbol()));
            long newQty = p.getQuantity() + order.getQuantity();
            BigDecimal cost = p.getAvgCost().multiply(BigDecimal.valueOf(p.getQuantity())).add(value);
            p.setAvgCost(cost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP));
            p.setQuantity(newQty);
            positions.save(p);
            account.setCash(account.getCash().subtract(value));
        } else {
            Position p = held.filter(h -> h.getQuantity() >= order.getQuantity())
                    .orElseThrow(() -> new DomainException(ErrorCode.INSUFFICIENT_HOLDINGS,
                            "Cannot sell " + order.getQuantity() + " " + order.getSymbol() + "; holding "
                                    + held.map(Position::getQuantity).orElse(0L) + ". Short selling is not supported."));
            if (p.getQuantity() == order.getQuantity()) {
                positions.delete(p);
            } else {
                p.setQuantity(p.getQuantity() - order.getQuantity());
            }
            account.setCash(account.getCash().add(value));
        }
        accounts.save(account);
        order.fill(price, now);
        record(account, order, "FILLED", actor, order.getQuantity() + " @ " + price);
    }

    private OrderView close(String userId, UUID orderId, OrderStatus to, String event,
                            List<OrderStatus> allowedFrom) {
        Account account = accountService.lock(userId);
        TradeOrder order = load(account, orderId);
        if (order.getStatus() != to) {
            if (!allowedFrom.contains(order.getStatus())) {
                throw new DomainException(ErrorCode.INVALID_STATE,
                        "Order is " + order.getStatus() + " and cannot be " + event.toLowerCase() + ".");
            }
            order.setStatus(to);
            record(account, order, event, "user:" + userId, null);
        }
        return OrderView.of(order);
    }

    private TradeOrder load(Account account, UUID orderId) {
        return orders.findByIdAndAccountId(orderId, account.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.ORDER_NOT_FOUND, "No such order: " + orderId));
    }

    private Quote quote(String symbol) {
        return marketData.quote(symbol).orElseThrow(() -> new DomainException(ErrorCode.QUOTE_UNAVAILABLE,
                "No quote available right now for " + symbol));
    }

    private void record(Account account, TradeOrder order, String type, String actor, String detail) {
        audit.save(new AuditEvent(account.getId(), order.getId(), type, actor, detail, clock.instant()));
    }

    private static String actorFor(OrderSource source, String userId) {
        return source == OrderSource.AI ? "ai-for:" + userId : "user:" + userId;
    }
}
