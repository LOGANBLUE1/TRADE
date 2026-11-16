import { useEffect, useMemo, useState } from "react";
import { api, ApiError, fmt, type Instrument } from "./api";
import type { Ticket } from "./App";

const REFRESH_MS = 60000;
const PAGE = 50;

type SortKey = "name" | "price" | "changePct" | "pe" | "marketCapCr";
const SORTS: { id: string; label: string; key: SortKey; desc: boolean }[] = [
  { id: "name", label: "Name (A to Z)", key: "name", desc: false },
  { id: "mcap", label: "Market cap (largest first)", key: "marketCapCr", desc: true },
  { id: "gain", label: "Biggest gainers today", key: "changePct", desc: true },
  { id: "loss", label: "Biggest losers today", key: "changePct", desc: false },
  { id: "pe", label: "PE (lowest first)", key: "pe", desc: false },
  { id: "price", label: "Price (highest first)", key: "price", desc: true },
];

export function MarketView(props: { onOpen: (symbol: string) => void; onTrade: (t: Ticket) => void }) {
  const [items, setItems] = useState<Instrument[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [industry, setIndustry] = useState("");
  const [sortId, setSortId] = useState("name");
  const [shown, setShown] = useState(PAGE);

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

  useEffect(() => setShown(PAGE), [text, industry, sortId]);

  const stocks = useMemo(() => (items ?? []).filter((i) => i.type === "EQUITY"), [items]);
  const industries = useMemo(
    () => [...new Set(stocks.map((s) => s.industry).filter((x): x is string => !!x))].sort(),
    [stocks],
  );
  const visible = useMemo(() => {
    const q = text.trim().toLowerCase();
    const sort = SORTS.find((s) => s.id === sortId)!;
    const value = (i: Instrument) => (sort.key === "name" ? null : i.data?.[sort.key] ?? null);
    return stocks
      .filter((s) => !industry || s.industry === industry)
      .filter((s) => !q || s.symbol.toLowerCase().includes(q) || s.name.toLowerCase().includes(q))
      .sort((a, b) => {
        if (sort.key === "name") return a.name.localeCompare(b.name);
        const x = value(a), y = value(b);
        if (x == null || y == null) return x == null ? (y == null ? 0 : 1) : -1;   // no data goes last
        return sort.desc ? y - x : x - y;
      });
  }, [stocks, text, industry, sortId]);

  if (error && !items) return <div className="notice notice-error" role="alert">{error}</div>;
  if (!items) return <p className="muted">Loading prices…</p>;

  const fx = items.filter((i) => i.type === "FX");
  const withData = stocks.filter((s) => s.data);
  const simulated = withData[0]?.data?.quote.source === "SIMULATED";
  const missing = stocks.length - withData.length;

  return (
    <section>
      {error && <div className="notice notice-error" role="alert">{error} Showing the last prices loaded.</div>}
      <h2>Stocks <span className="muted small">NIFTY 500 · {stocks.length} companies</span></h2>
      {missing > 0 && (
        <div className="notice notice-info">
          Prices are still loading for {missing} of {stocks.length} stocks. They fill in gradually in the background.
        </div>
      )}
      <div className="row">
        <input aria-label="Filter stocks" placeholder="Filter by name or symbol" value={text} onChange={(e) => setText(e.target.value)} />
        <select aria-label="Industry" value={industry} onChange={(e) => setIndustry(e.target.value)}>
          <option value="">All industries</option>
          {industries.map((i) => <option key={i} value={i}>{i}</option>)}
        </select>
        <select aria-label="Sort by" value={sortId} onChange={(e) => setSortId(e.target.value)}>
          {SORTS.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
        </select>
      </div>

      {visible.length === 0 ? (
        <p className="muted">No stock matches these filters.</p>
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Symbol</th><th>Name</th><th>Industry</th><th className="r">Price</th>
                  <th className="r">Change today</th><th className="r">PE</th><th className="r">Market cap (₹ cr)</th><th />
                </tr>
              </thead>
              <tbody>
                {visible.slice(0, shown).map((i) => (
                  <tr key={i.symbol} className="clickable" onClick={() => props.onOpen(i.symbol)}>
                    <td><button className="link sym" onClick={(e) => { e.stopPropagation(); props.onOpen(i.symbol); }}>{i.symbol}</button></td>
                    <td className="wrap">{i.name}</td>
                    <td className="muted wrap">{i.industry}</td>
                    <td className="r">{i.data ? fmt.money(i.data.price) : "–"}</td>
                    <td className={"r " + (i.data?.changePct != null && i.data.changePct < 0 ? "down" : "up")}>{fmt.pct(i.data?.changePct)}</td>
                    <td className="r">{fmt.num(i.data?.pe)}</td>
                    <td className="r">{fmt.num(i.data?.marketCapCr)}</td>
                    <td className="r">
                      <button className="btn btn-small" disabled={!i.data}
                              onClick={(e) => { e.stopPropagation(); props.onTrade({ symbol: i.symbol, side: "BUY" }); }}>Buy</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="row">
            <span className="muted small">Showing {Math.min(shown, visible.length)} of {visible.length}</span>
            {shown < visible.length && <button className="btn" onClick={() => setShown(shown + PAGE)}>Show {Math.min(PAGE, visible.length - shown)} more</button>}
          </div>
        </>
      )}

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
                {fx.map((i) => (
                  <tr key={i.symbol} className="clickable" onClick={() => props.onOpen(i.symbol)}>
                    <td className="sym">{i.name}</td>
                    <td><button className="link" onClick={(e) => { e.stopPropagation(); props.onOpen(i.symbol); }}>{i.symbol}</button></td>
                    <td className="r">{fmt.num4(i.data?.price)}</td>
                    <td className={"r " + (i.data?.changePct != null && i.data.changePct < 0 ? "down" : "up")}>{fmt.pct(i.data?.changePct)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      <p className="muted small">
        {simulated ? "Prices and fundamentals are simulated sample data, not real market data." : "Prices are delayed by about 15 minutes. “–” means the price source does not provide that figure."}
      </p>
    </section>
  );
}
