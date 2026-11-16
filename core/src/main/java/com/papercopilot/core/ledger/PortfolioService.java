package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.papercopilot.core.ledger.PortfolioView.PositionView;
import com.papercopilot.core.marketdata.MarketDataProvider;
import com.papercopilot.core.marketdata.Quote;

@Service
public class PortfolioService {

    private final AccountService accountService;
    private final PositionRepository positions;
    private final AuditEventRepository audit;
    private final MarketDataProvider marketData;
    private final Clock clock;

    PortfolioService(AccountService accountService, PositionRepository positions, AuditEventRepository audit,
                     MarketDataProvider marketData, Clock clock) {
        this.accountService = accountService;
        this.positions = positions;
        this.audit = audit;
        this.marketData = marketData;
        this.clock = clock;
    }

    @Transactional
    public PortfolioView get(String userId) {
        Account account = accountService.lock(userId);
        List<PositionView> views = new ArrayList<>();
        BigDecimal positionsValue = BigDecimal.ZERO;
        for (Position p : positions.findByAccountIdOrderBySymbol(account.getId())) {
            BigDecimal qty = BigDecimal.valueOf(p.getQuantity());
            BigDecimal last = marketData.quote(p.getSymbol()).map(Quote::price).orElse(null);
            // Without a live quote, carry the position at cost so total equity does not drop to cash.
            BigDecimal value = (last != null ? last : p.getAvgCost()).multiply(qty);
            positionsValue = positionsValue.add(value);
            views.add(new PositionView(p.getSymbol(), p.getQuantity(), p.getAvgCost(), last,
                    last == null ? null : value,
                    last == null ? null : last.subtract(p.getAvgCost()).multiply(qty)));
        }
        return new PortfolioView(account.getCash(), account.getCurrency(), views, positionsValue,
                account.getCash().add(positionsValue), clock.instant());
    }

    @Transactional
    public List<AuditView> auditTrail(String userId) {
        Account account = accountService.lock(userId);
        return audit.findTop200ByAccountIdOrderByIdDesc(account.getId()).stream()
                .map(e -> new AuditView(e.getId(), e.getOrderId(), e.getEventType(), e.getActor(), e.getDetail(),
                        e.getCreatedAt()))
                .toList();
    }
}
