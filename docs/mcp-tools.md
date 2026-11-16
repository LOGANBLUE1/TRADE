# MCP tools

Server: streamable HTTP at `/mcp`, name `paper-trading-copilot`. Six tools. None of them approves an order.

Results are JSON text. Failures come back as `isError: true` with text `CODE: message`, for example
`APPROVAL_REQUIRED: Order ... has not been approved by the user yet.`

## Trade flow

1. `propose_trade` -> order in `PENDING_APPROVAL`, returns its `id`.
2. The user approves in the app: `POST /api/orders/{id}/approve`. Only the REST API can do this.
3. `execute_trade` with that `id` -> `FILLED`. Before approval it fails with `APPROVAL_REQUIRED`.

Approvals last 15 minutes. If the price has moved more than 2% from the proposal, execution fails with
`PRICE_MOVED`.

## get_quote

Input:

```json
{"symbol": "RELIANCE.NS"}
```

Output:

```json
{"symbol": "RELIANCE.NS", "name": "Reliance Industries", "price": 1480.5, "previousClose": 1468.0,
 "changePct": 0.85, "currency": "INR", "asOf": "2026-11-16T14:34:10Z", "delayed": false, "source": "SIMULATED"}
```

Errors: `SYMBOL_NOT_ALLOWED`, `QUOTE_UNAVAILABLE`.

## screen_stocks

Input: a `filter`. Each condition has `field`, `op`, and exactly one of `value` or `otherField`.

```json
{"filter": {
  "conditions": [
    {"field": "PE", "op": "LT", "value": 20},
    {"field": "PRICE", "op": "GT", "otherField": "SMA_50"}
  ],
  "sortBy": "PE", "sortDescending": false, "limit": 10
}}
```

- Fields: `PRICE`, `CHANGE_PCT`, `PE`, `MARKET_CAP_CR`, `VOLUME`, `AVG_VOLUME_20D`, `VOLUME_RATIO`, `SMA_50`, `SMA_200`
- Ops: `LT`, `LTE`, `GT`, `GTE`
- At most 12 conditions; `limit` 1-100.

Output: `{filter, universeSize, unavailable, matches: [...], asOf}`. Each match carries `symbol`, `name`,
`price`, `changePct`, `pe`, `marketCapCr`, `volume`, `avgVolume20d`, `sma50`, `sma200` and its `quote`.
`unavailable` counts symbols with no data at the moment. Errors: `INVALID_FILTER`.

## get_portfolio

No input. Output:

```json
{"cash": 983877.0, "currency": "INR",
 "positions": [{"symbol": "INFY.NS", "quantity": 10, "avgCost": 1612.3, "lastPrice": 1612.3,
                "marketValue": 16123.0, "unrealizedPnl": 0.0}],
 "positionsValue": 16123.0, "totalEquity": 1000000.0, "asOf": "2026-11-16T14:34:11Z"}
```

## propose_trade

Input (`rationale` optional):

```json
{"symbol": "INFY.NS", "side": "BUY", "quantity": 10, "rationale": "Matches the low-PE screen",
 "idempotencyKey": "5b0c1c0e-..."}
```

Output: the order, with `"status": "PENDING_APPROVAL"` and `referencePrice` (the quote at proposal time).
Sending the same `idempotencyKey` again returns the same order.

Errors: `SYMBOL_NOT_ALLOWED`, `ORDER_TOO_LARGE`, `DAILY_LIMIT_REACHED`, `IDEMPOTENCY_CONFLICT`,
`INVALID_REQUEST`, `QUOTE_UNAVAILABLE`.

## get_order

Input: `{"orderId": "..."}`. Output: the order. Use it to check whether the user has approved.

## execute_trade

Input: `{"orderId": "..."}`. Output: the order with `"status": "FILLED"`, `fillPrice`, `filledAt`.
Calling it again on a filled order returns the same order without trading twice.

Errors: `APPROVAL_REQUIRED`, `APPROVAL_EXPIRED`, `PRICE_MOVED`, `INSUFFICIENT_FUNDS`,
`INSUFFICIENT_HOLDINGS`, `INVALID_STATE`, `ORDER_NOT_FOUND`.

## Order object

```json
{"id": "2cd05204-...", "symbol": "INFY.NS", "side": "BUY", "quantity": 10, "status": "FILLED",
 "source": "AI", "referencePrice": 1612.3, "fillPrice": 1612.3, "rationale": "...",
 "createdAt": "...", "approvedAt": "...", "approvedBy": "user:demo", "filledAt": "..."}
```

Statuses: `PENDING_APPROVAL`, `APPROVED`, `FILLED`, `REJECTED`, `CANCELLED`, `EXPIRED`.
