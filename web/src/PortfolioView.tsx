import { fmt, type Portfolio } from "./api";
import type { Ticket } from "./App";

export function PortfolioView(props: { portfolio: Portfolio | null; onTrade: (t: Ticket) => void }) {
  const p = props.portfolio;
  if (!p) return <p className="muted">Loading your account…</p>;

  const pnl = p.positions.reduce((sum, x) => sum + (x.unrealizedPnl ?? 0), 0);
  return (
    <section>
      <div className="stats">
        <Stat label="Total value" value={fmt.money(p.totalEquity)} />
        <Stat label="Cash" value={fmt.money(p.cash)} />
        <Stat label="Invested" value={fmt.money(p.positionsValue)} />
        <Stat label="Unrealised gain/loss" value={fmt.money(pnl)} tone={pnl > 0 ? "up" : pnl < 0 ? "down" : undefined} />
      </div>

      <h2>Holdings</h2>
      {p.positions.length === 0 ? (
        <div className="empty">
          <p>You don't hold anything yet.</p>
          <button className="btn btn-primary" onClick={() => props.onTrade({ symbol: "", side: "BUY" })}>
            Place your first order
          </button>
        </div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Symbol</th><th className="r">Qty</th><th className="r">Avg cost</th>
                <th className="r">Last price</th><th className="r">Value</th><th className="r">Gain/loss</th><th />
              </tr>
            </thead>
            <tbody>
              {p.positions.map((x) => (
                <tr key={x.symbol}>
                  <td className="sym">{x.symbol}</td>
                  <td className="r">{fmt.num(x.quantity)}</td>
                  <td className="r">{fmt.money(x.avgCost)}</td>
                  <td className="r">{x.lastPrice == null ? "No quote" : fmt.money(x.lastPrice)}</td>
                  <td className="r">{fmt.money(x.marketValue)}</td>
                  <td className={"r " + tone(x.unrealizedPnl)}>{fmt.money(x.unrealizedPnl)}</td>
                  <td className="r actions">
                    <button className="btn btn-small" onClick={() => props.onTrade({ symbol: x.symbol, side: "BUY" })}>Buy</button>
                    <button className="btn btn-small" onClick={() => props.onTrade({ symbol: x.symbol, side: "SELL" })}>Sell</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function Stat(props: { label: string; value: string; tone?: "up" | "down" }) {
  return (
    <div className="stat">
      <div className="stat-label">{props.label}</div>
      <div className={"stat-value " + (props.tone ?? "")}>{props.value}</div>
    </div>
  );
}

const tone = (v: number | null) => (v == null || v === 0 ? "" : v > 0 ? "up" : "down");
