# Paper-Trading Copilot

A paper-trading (demo money) stock app for NSE equities with an AI copilot on top:
plain-language screens, MCP tools for an assistant, and a human approval gate on every AI-proposed trade.

No real money moves anywhere in this project.

## Status

| Piece | State |
|---|---|
| Quotes, structured screener, paper ledger, orders, audit log (REST) | Working, tested |
| Approval flow: `propose_trade` -> user approves -> `execute_trade` | Working, tested |
| MCP server with 6 tools at `/mcp` | Working |
| Plain-language screen (`/api/ai/screen`) and AI credits | Working against a stubbed model; not yet run against a real LLM |
| Yahoo market data provider | Written, parser unit-tested, never run against the live endpoint |
| Authentication | Not built. `X-User-Id` header is a development stand-in |
| React UI (`web/`): portfolio, market prices, stock search and detail, order ticket, screener, approvals, activity | Working, checked in a browser |
| RAG over news and filings, saved screens | Not started |

## The rule this codebase is built around

**AI is a client of the trading core, never part of it.** If a user runs out of AI credits, or the LLM
provider is down, quotes, screening with manual filters, buy/sell, portfolio and orders keep working.

```
React UI --REST--> core: QuoteService / ScreenerService / PortfolioService / OrderService --> Postgres, MarketDataProvider
                     ^
ai: chat + MCP tools-┘   (thin adapters; the credit check lives only here)
```

How that is enforced:

- `core` is a Maven module with no Spring AI dependency, so it cannot call an LLM.
- The credit gate is an interceptor registered for `/api/ai/**` only.
- `ArchitectureTest` fails the build if `core` or the REST API depends on the AI module, or if anything in
  the AI module calls `OrderService.approve` / `placeManual`.
- `AiIsolationTest` exhausts a user's credits, and separately breaks the model, then checks that every core
  endpoint still returns 200.

## Modules

| Module | Contents |
|---|---|
| `core` | Market data (`MarketDataProvider`), screener (`FilterSpec`), ledger, order state machine, audit, Flyway schema |
| `ai` | Plain-language -> `FilterSpec`, AI credits, MCP tools |
| `app` | Spring Boot application, REST controllers, configuration, integration tests |
| `web` | React + TypeScript UI (Vite) |

## Run it

Needs Java 21, Maven and Docker.

```bash
docker compose up -d
```

```bash
mvn install
```

```bash
java -jar app/target/app-0.1.0-SNAPSHOT.jar
```

Then the UI, in a second terminal (needs Node 20+):

```bash
cd web && npm install && npm run dev
```

Open http://localhost:5173. The dev server forwards `/api` to the backend on port 8080.

The app starts with AI switched off and simulated prices. To turn on the plain-language screen:

```bash
COPILOT_CHAT_MODEL=anthropic ANTHROPIC_API_KEY=... java -jar app/target/app-0.1.0-SNAPSHOT.jar
```

`ANTHROPIC_MODEL` picks the model; choose a cheaper one if cost per screen matters.
`MARKET_DATA_PROVIDER=yahoo` switches to delayed Yahoo data.

## REST API (works without AI)

All take an optional `X-User-Id` header (default `demo`).

| Method and path | Purpose |
|---|---|
| `GET /api/instruments?q=` | All allowed stocks and FX pairs with prices; `q` searches symbol and name |
| `GET /api/instruments/{symbol}` | One instrument's details (price, PE, market cap, volume, averages) |
| `GET /api/quotes/{symbol}` | Quote for an allowed symbol (`RELIANCE`, `TCS.NS`, `GBPUSD=X`) |
| `POST /api/screens/run` | Run a `FilterSpec` |
| `GET /api/portfolio` | Cash, positions, valuation |
| `GET /api/orders?status=PENDING_APPROVAL&status=APPROVED` | Orders; the filter shown gives active orders |
| `POST /api/orders` (+ `Idempotency-Key` header) | Manual buy/sell, filled immediately |
| `POST /api/orders/{id}/approve` \| `reject` \| `cancel` \| `execute` | Act on a proposed order |
| `GET /api/audit` | Proposal, approval, fill, rejection events |
| `GET /api/me/entitlements` | Whether AI is usable and credits left; never fails |

AI: `POST /api/ai/screen` with `{"query": "PE under 20, volume rising, above 50-day average"}`.
Returns `402 AI_CREDITS_EXHAUSTED` or `503 AI_UNAVAILABLE` when it cannot run.

Example screen body:

```json
{
  "conditions": [
    {"field": "PE", "op": "LT", "value": 20},
    {"field": "VOLUME_RATIO", "op": "GT", "value": 1},
    {"field": "PRICE", "op": "GT", "otherField": "SMA_50"}
  ],
  "sortBy": "PE",
  "sortDescending": false
}
```

MCP tools are described in [docs/mcp-tools.md](docs/mcp-tools.md).

## Guardrails

| Guardrail | Where |
|---|---|
| Approval checked server-side on every execution | `OrderService.execute` |
| No MCP tool can approve | `TradingTools` has none; `ArchitectureTest` |
| Idempotency keys, unique per account | `orders` table constraint + `OrderService` |
| Max order value and quantity, daily order count, allowed symbols | `copilot.trading.*`, `copilot.universe.*` |
| Approval expires (15 min) and price-drift check (2%) before fill | `OrderService.execute` |
| No short selling, no overdraft | `OrderService`, DB check constraints |
| Audit log | `audit_events` |
| LLM output treated as untrusted | Filter validated before it runs; the model never supplies prices |

## Known gaps

- **No authentication.** Anyone who can reach the server can act as any user, and the MCP endpoint acts as
  `copilot.ai.mcp-user-id`. Do not expose this beyond localhost until real auth is in place.
- **Simulated prices are invented**, fixed numbers. They are labelled `source: SIMULATED`.
- **Yahoo gives no PE or market cap** through the endpoint used, so screens on those fields return nothing
  with that provider. It is also unofficial and unlicensed: fine for a personal demo, not for a product.
- **FX pairs are quote-only.** The ledger is single-currency (INR).
- Market orders only; fills are instant at the quoted price with no fees or market-hours check.
