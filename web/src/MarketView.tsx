import { useEffect, useState } from "react";
import { api, ApiError, fmt, type Instrument } from "./api";
import type { Ticket } from "./App";

const REFRESH_MS = 15000;

export function MarketView(props: { onOpen: (symbol: string) => void; onTrade: (t: Ticket) => void }) {
  const [items, setItems] = useState<Instrument[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let stale = false;
    const load = () =>
      api.instruments()
        .then((r) => { if (!stale) { setItems(r); setError(null); } })
        .catch((e) => { if (!stale) setError(e instanceof ApiError ? e.message : "Prices could not be loaded."); });
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => { stale = true; clearInterval(timer); };
  }, []);

  if (error && !items) return <div className="notice notice-error" role="alert">{error}</div>;
  if (!items) return <p className="muted">Loading prices…</p>;

  const stocks = items.filter((i) => i.type === "EQUITY");
  const fx = items.filter((i) => i.type === "FX");
  const simulated = items[0]?.data.quote.source === "SIMULATED";

  return (
    <section>
      {error && <div className="notice notice-error" role="alert">{error} Showing the last prices loaded.</div>}
      <h2>Stocks</h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Symbol</th><th>Name</th><th className="r">Price</th><th className="r">Change today</th>
              <th className="r">PE</th><th className="r">Market cap (₹ cr)</th><th />
            </tr>
          </thead>
          <tbody>
            {stocks.map(({ data: s }) => (
              <tr key={s.symbol} className="clickable" onClick={() => props.onOpen(s.symbol)}>
                <td><button className="link sym" onClick={(e) => { e.stopPropagation(); props.onOpen(s.symbol); }}>{s.symbol}</button></td>
                <td>{s.name}</td>
                <td className="r">{fmt.money(s.price)}</td>
                <td className={"r " + (s.changePct != null && s.changePct < 0 ? "down" : "up")}>{fmt.pct(s.changePct)}</td>
                <td className="r">{fmt.num(s.pe)}</td>
                <td className="r">{fmt.num(s.marketCapCr)}</td>
                <td className="r">
                  <button className="btn btn-small" onClick={(e) => { e.stopPropagation(); props.onTrade({ symbol: s.symbol, side: "BUY" }); }}>Buy</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {fx.length > 0 && (
        <>
          <h2>Currency pairs</h2>
          <p className="muted small">Prices only. Currency pairs cannot be traded on the paper account yet.</p>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Pair</th><th>Symbol</th><th className="r">Rate</th><th className="r">Change today</th></tr>
              </thead>
              <tbody>
                {fx.map(({ data: s }) => (
                  <tr key={s.symbol} className="clickable" onClick={() => props.onOpen(s.symbol)}>
                    <td className="sym">{s.name}</td>
                    <td><button className="link" onClick={(e) => { e.stopPropagation(); props.onOpen(s.symbol); }}>{s.symbol}</button></td>
                    <td className="r">{fmt.num4(s.price)}</td>
                    <td className={"r " + (s.changePct != null && s.changePct < 0 ? "down" : "up")}>{fmt.pct(s.changePct)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      <p className="muted small">
        {simulated ? "Prices and fundamentals are simulated sample data, not real market data." : "Prices are delayed by about 15 minutes."}
      </p>
    </section>
  );
}
