package com.papercopilot.core.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface AccountRepository extends JpaRepository<Account, UUID> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into accounts (id, user_id, cash) values (:id, :userId, :cash)
            on conflict (user_id) do nothing""")
    int insertIfAbsent(UUID id, String userId, BigDecimal cash);

    /** Row lock: every mutation for one account runs one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.userId = :userId")
    Optional<Account> lockByUserId(String userId);

    Optional<Account> findByUserId(String userId);
}

interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByAccountIdOrderBySymbol(UUID accountId);

    Optional<Position> findByAccountIdAndSymbol(UUID accountId, String symbol);
}

interface TradeOrderRepository extends JpaRepository<TradeOrder, UUID> {

    Optional<TradeOrder> findByIdAndAccountId(UUID id, UUID accountId);

    Optional<TradeOrder> findByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);

    List<TradeOrder> findTop200ByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<TradeOrder> findTop200ByAccountIdAndStatusInOrderByCreatedAtDesc(UUID accountId,
                                                                         Collection<OrderStatus> statuses);

    long countByAccountIdAndCreatedAtGreaterThanEqualAndStatusNotIn(UUID accountId, Instant since,
                                                                    Collection<OrderStatus> statuses);
}

interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop200ByAccountIdOrderByIdDesc(UUID accountId);
}
