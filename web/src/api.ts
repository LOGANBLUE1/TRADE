export type Field =
  | "PRICE" | "CHANGE_PCT" | "PE" | "MARKET_CAP_CR" | "VOLUME"
  | "AVG_VOLUME_20D" | "VOLUME_RATIO" | "SMA_50" | "SMA_200";
export type Op = "LT" | "LTE" | "GT" | "GTE";

export interface Condition { field: Field; op: Op; value?: number | null; otherField?: Field | null }
export interface FilterSpec {
  conditions: Condition[];
  sortBy?: Field | null;
  sortDescending?: boolean | null;
  limit?: number | null;
}

export interface Quote {
  symbol: string; name: string; price: number; previousClose: number | null; changePct: number | null;
  currency: string; asOf: string; delayed: boolean; source: string;
}
export interface Snapshot {
  symbol: string; name: string; price: number; changePct: number | null; pe: number | null;
  marketCapCr: number | null; volume: number | null; avgVolume20d: number | null;
  sma50: number | null; sma200: number | null; quote: Quote;
}
export interface Instrument { type: "EQUITY" | "FX"; tradable: boolean; data: Snapshot }
export interface ScreenResult {
  filter: FilterSpec; universeSize: number; unavailable: number; matches: Snapshot[]; asOf: string;
}
export interface Position {
  symbol: string; quantity: number; avgCost: number; lastPrice: number | null;
  marketValue: number | null; unrealizedPnl: number | null;
}
export interface Portfolio {
  cash: number; currency: string; positions: Position[]; positionsValue: number; totalEquity: number; asOf: string;
}
export type OrderStatus = "PENDING_APPROVAL" | "APPROVED" | "FILLED" | "REJECTED" | "CANCELLED" | "EXPIRED";
export interface Order {
  id: string; symbol: string; side: "BUY" | "SELL"; quantity: number; status: OrderStatus;
  source: "MANUAL" | "AI"; referencePrice: number; fillPrice: number | null; rationale: string | null;
  createdAt: string; approvedAt: string | null; approvedBy: string | null; filledAt: string | null;
}
export interface AuditEvent {
  id: number; orderId: string | null; eventType: string; actor: string; detail: string | null; at: string;
}
export interface Entitlements { aiEnabled: boolean; aiModelConfigured: boolean; aiCreditsRemaining: number }
export interface NlScreenResponse { query: string; result: ScreenResult; creditsRemaining: number }

/** An error the server explained ({code, message}), or a network/HTTP failure (code "NETWORK" / "HTTP_nnn"). */
export class ApiError extends Error {
  constructor(public code: string, message: string) {
    super(message);
  }
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      ...init,
      headers: { "Content-Type": "application/json", ...(init.headers ?? {}) },
    });
  } catch {
    throw new ApiError("NETWORK", "Cannot reach the server. Is the backend running on port 8080?");
  }
  const text = await res.text();
  let body: unknown = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    /* not JSON: handled below */
  }
  if (!res.ok) {
    const b = body as { code?: string; message?: string } | null;
    throw new ApiError(b?.code ?? `HTTP_${res.status}`, b?.message ?? `The server answered ${res.status}.`);
  }
  return body as T;
}

const post = <T,>(path: string, body?: unknown, headers?: Record<string, string>) =>
  call<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body), headers });

export const api = {
  entitlements: () => call<Entitlements>("/api/me/entitlements"),
  quote: (symbol: string) => call<Quote>(`/api/quotes/${encodeURIComponent(symbol)}`),
  instruments: (q = "") => call<Instrument[]>(`/api/instruments${q ? `?q=${encodeURIComponent(q)}` : ""}`),
  instrument: (symbol: string) => call<Instrument>(`/api/instruments/${encodeURIComponent(symbol)}`),
  portfolio: () => call<Portfolio>("/api/portfolio"),
  orders: () => call<Order[]>("/api/orders"),
  audit: () => call<AuditEvent[]>("/api/audit"),
  runScreen: (filter: FilterSpec) => post<ScreenResult>("/api/screens/run", filter),
  aiScreen: (query: string) => post<NlScreenResponse>("/api/ai/screen", { query }),
  placeOrder: (symbol: string, side: "BUY" | "SELL", quantity: number, idempotencyKey: string) =>
    post<Order>("/api/orders", { symbol, side, quantity }, { "Idempotency-Key": idempotencyKey }),
  orderAction: (id: string, action: "approve" | "reject" | "cancel" | "execute") =>
    post<Order>(`/api/orders/${id}/${action}`),
};

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });
const num = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 });

export const fmt = {
  money: (v: number | null | undefined) => (v == null ? "–" : inr.format(v)),
  price: (v: number | null | undefined, currency = "INR") =>
    v == null ? "–" : currency === "INR" ? inr.format(v) : `${num.format(v)} ${currency}`,
  num: (v: number | null | undefined) => (v == null ? "–" : num.format(v)),
  /** Exchange rates need more decimals than share prices. */
  num4: (v: number | null | undefined) => (v == null ? "–" : v.toFixed(4)),
  pct: (v: number | null | undefined) => (v == null ? "–" : `${v > 0 ? "+" : ""}${v.toFixed(2)}%`),
  time: (iso: string | null | undefined) =>
    iso ? new Date(iso).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "–",
};
