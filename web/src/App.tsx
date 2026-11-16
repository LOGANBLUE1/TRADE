import { useCallback, useEffect, useState } from "react";
import { api, ApiError, type AuditEvent, type Entitlements, type Order, type Portfolio } from "./api";
import { PortfolioView } from "./PortfolioView";
import { Screener } from "./Screener";
import { OrdersView } from "./OrdersView";
import { ActivityView } from "./ActivityView";
import { TradeTicket } from "./TradeTicket";
import { MarketView } from "./MarketView";
import { StockDetail } from "./StockDetail";
import { Search } from "./Search";

type Tab = "portfolio" | "market" | "screener" | "orders" | "activity";

export interface Ticket { symbol: string; side: "BUY" | "SELL" }

const REFRESH_MS = 5000;

type Theme = "light" | "dark";

/** Light unless the user switched to dark on this browser before. */
function savedTheme(): Theme {
  try {
    return localStorage.getItem("theme") === "dark" ? "dark" : "light";
  } catch {
    return "light";
  }
}

export function App() {
  const [tab, setTab] = useState<Tab>("portfolio");
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [entitlements, setEntitlements] = useState<Entitlements | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [theme, setTheme] = useState<Theme>(savedTheme);
  // When set, the stock page is shown in place of the current tab.
  const [detail, setDetail] = useState<string | null>(null);
  const selectTab = (t: Tab) => { setDetail(null); setTab(t); };

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem("theme", theme);
    } catch {
      /* private mode: the choice just lasts for this visit */
    }
  }, [theme]);

  // Core data and AI status load independently: an AI problem must never blank the trading screens.
  const refresh = useCallback(async () => {
    try {
      const [p, o, a] = await Promise.all([api.portfolio(), api.orders(), api.audit()]);
      setPortfolio(p);
      setOrders(o);
      setAudit(a);
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : "Something went wrong loading your account.");
    }
    api.entitlements().then(setEntitlements).catch(() => setEntitlements(null));
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, REFRESH_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  const waiting = orders.filter((o) => o.status === "PENDING_APPROVAL").length;

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-name">Paper-Trading Copilot</span>
          <span className="badge badge-warn" title="No real money is involved">Paper money</span>
        </div>
        <Search onOpen={setDetail} />
        <div className="topbar-right">
          <AiStatus entitlements={entitlements} />
          <button className="btn btn-small" aria-pressed={theme === "dark"}
                  onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>
            {theme === "dark" ? "Light mode" : "Dark mode"}
          </button>
        </div>
      </header>

      <nav className="tabs" aria-label="Sections">
        <TabButton id="portfolio" current={detail ? null : tab} onSelect={selectTab}>Portfolio</TabButton>
        <TabButton id="market" current={detail ? null : tab} onSelect={selectTab}>Market</TabButton>
        <TabButton id="screener" current={detail ? null : tab} onSelect={selectTab}>Screener</TabButton>
        <TabButton id="orders" current={detail ? null : tab} onSelect={selectTab}>
          Orders{waiting > 0 && <span className="count" aria-label={`${waiting} waiting for approval`}>{waiting}</span>}
        </TabButton>
        <TabButton id="activity" current={detail ? null : tab} onSelect={selectTab}>Activity</TabButton>
        <button className="btn btn-primary tabs-action" onClick={() => setTicket({ symbol: "", side: "BUY" })}>
          New order
        </button>
      </nav>

      {loadError && <div className="notice notice-error" role="alert">{loadError}</div>}
      {waiting > 0 && (detail || tab !== "orders") && (
        <button className="notice notice-info notice-button" onClick={() => selectTab("orders")}>
          {waiting === 1 ? "1 order is" : `${waiting} orders are`} waiting for your approval. Review
        </button>
      )}

      <main>
        {detail ? (
          <StockDetail key={detail} symbol={detail} portfolio={portfolio} onBack={() => setDetail(null)} onTrade={setTicket} />
        ) : (
          <>
            {tab === "portfolio" && <PortfolioView portfolio={portfolio} onTrade={setTicket} onOpen={setDetail} />}
            {tab === "market" && <MarketView onOpen={setDetail} onTrade={setTicket} />}
            {tab === "screener" && <Screener entitlements={entitlements} onTrade={setTicket} onOpen={setDetail} onAiUsed={refresh} />}
            {tab === "orders" && <OrdersView orders={orders} onChanged={refresh} />}
            {tab === "activity" && <ActivityView events={audit} />}
          </>
        )}
      </main>

      {ticket && (
        <TradeTicket
          initial={ticket}
          portfolio={portfolio}
          onClose={() => setTicket(null)}
          onFilled={refresh}
        />
      )}
    </div>
  );
}

function TabButton(props: { id: Tab; current: Tab | null; onSelect: (t: Tab) => void; children: React.ReactNode }) {
  const active = props.id === props.current;
  return (
    <button className={active ? "tab tab-active" : "tab"} aria-current={active ? "page" : undefined}
            onClick={() => props.onSelect(props.id)}>
      {props.children}
    </button>
  );
}

function AiStatus({ entitlements }: { entitlements: Entitlements | null }) {
  if (!entitlements) return <span className="badge">AI status unknown</span>;
  if (!entitlements.aiModelConfigured) {
    return <span className="badge" title="The server has no AI model configured">AI off</span>;
  }
  if (entitlements.aiCreditsRemaining <= 0) return <span className="badge badge-warn">AI credits used up</span>;
  return <span className="badge badge-ok">AI credits: {entitlements.aiCreditsRemaining}</span>;
}
