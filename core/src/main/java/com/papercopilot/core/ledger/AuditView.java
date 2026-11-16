package com.papercopilot.core.ledger;

import java.time.Instant;
import java.util.UUID;

public record AuditView(long id, UUID orderId, String eventType, String actor, String detail, Instant at) {}
