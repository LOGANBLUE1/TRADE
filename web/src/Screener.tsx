import { useState } from "react";
import { api, ApiError, fmt, type Condition, type Entitlements, type Field, type FilterSpec, type Op, type ScreenResult } from "./api";
import type { Ticket } from "./App";

const FIELDS: { id: Field; label: string }[] = [
  { id: "PRICE", label: "Price" },
  { id: "CHANGE_PCT", label: "Change today (%)" },
  { id: "PE", label: "PE ratio" },
  { id: "MARKET_CAP_CR", label: "Market cap (₹ crore)" },
  { id: "VOLUME", label: "Volume today" },
  { id: "AVG_VOLUME_20D", label: "Average volume (20 days)" },
  { id: "VOLUME_RATIO", label: "Volume vs average (×)" },
  { id: "SMA_50", label: "50-day average price" },
  { id: "SMA_200", label: "200-day average price" },
];
const OPS: { id: Op; label: string }[] = [
  { id: "LT", label: "is below" },
  { id: "LTE", label: "is at most" },
  { id: "GT", label: "is above" },
  { id: "GTE", label: "is at least" },
];

/** One row of the builder. `against` is "number" or the field to compare with. */
interface Row { field: Field; op: Op; against: "number" | Field; value: string }

const STARTER: Row[] = [
  { field: "PE", op: "LT", against: "number", value: "20" },
  { field: "VOLUME_RATIO", op: "GT", against: "number", value: "1" },
  { field: "PRICE", op: "GT", against: "SMA_50", value: "" },
];

const toRows = (f: FilterSpec): Row[] =>
  f.conditions.map((c) => ({
    field: c.field,
    op: c.op,
    against: c.otherField ?? "number",
    value: c.value == null ? "" : String(c.value),
  }));

export function Screener(props: {
  entitlements: Entitlements | null;
  onTrade: (t: Ticket) => void;
  onOpen: (symbol: string) => void;
  onAiUsed: () => void;
}) {
  const [rows, setRows] = useState<Row[]>(STARTER);
  const [result, setResult] = useState<ScreenResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState("");
  const [aiBusy, setAiBusy] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);

  const update = (i: number, patch: Partial<Row>) => setRows(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const incomplete = rows.some((r) => r.against === "number" && (r.value.trim() === "" || Number.isNaN(Number(r.value))));

  async function run() {
    setBusy(true);
    setError(null);
    try {
      const conditions: Condition[] = rows.map((r) =>
        r.against === "number" ? { field: r.field, op: r.op, value: Number(r.value) } : { field: r.field, op: r.op, otherField: r.against });
      setResult(await api.runScreen({ conditions }));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "The screen could not be run.");
    } finally {
      setBusy(false);
    }
  }

  async function askAi(e: React.FormEvent) {
    e.preventDefault();
    if (!query.trim() || aiBusy) return;
    setAiBusy(true);
    setAiError(null);
    try {
      const res = await api.aiScreen(query.trim());
      setRows(toRows(res.result.filter));   // show what the AI understood, so it can be checked and edited
      setResult(res.result);
      setError(null);
    } catch (err) {
      setAiError(err instanceof ApiError ? err.message : "The AI screen failed. You can still build the filters below.");
    } finally {
      setAiBusy(false);
      props.onAiUsed();
    }
  }

  const ent = props.entitlements;
  const aiOff = !ent?.aiEnabled;
  const aiHint = !ent ? "AI status could not be loaded. Build the filters below instead."
    : !ent.aiModelConfigured ? "AI is switched off on this server. Build the filters below instead."
    : ent.aiCreditsRemaining <= 0 ? "Your AI credits are used up. Filters below work as usual."
    : "The AI turns this into the filters below, which you can check and edit. Uses 1 credit.";

  return (
    <section>
      <form className="card" onSubmit={askAi}>
        <label htmlFor="nl">Describe a screen</label>
        <div className="row">
          <input id="nl" value={query} maxLength={500} disabled={aiOff || aiBusy}
                 onChange={(e) => setQuery(e.target.value)}
                 placeholder="e.g. PE under 20, volume rising, above the 50-day average" />
          <button className="btn btn-primary" disabled={aiOff || aiBusy || !query.trim()}>
            {aiBusy ? "Thinking…" : "Ask AI"}
          </button>
        </div>
        <div className="muted small">{aiHint}</div>
        {aiError && <div className="notice notice-error" role="alert">{aiError}</div>}
      </form>

      <div className="card">
        <h2>Filters</h2>
        {rows.length === 0 && <p className="muted">No filters: running the screen lists every stock.</p>}
        {rows.map((r, i) => (
          <div className="filter-row" key={i}>
            <select aria-label="Field" value={r.field} onChange={(e) => update(i, { field: e.target.value as Field })}>
              {FIELDS.map((f) => <option key={f.id} value={f.id}>{f.label}</option>)}
            </select>
            <select aria-label="Comparison" value={r.op} onChange={(e) => update(i, { op: e.target.value as Op })}>
              {OPS.map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
            </select>
            <select aria-label="Compare with" value={r.against} onChange={(e) => update(i, { against: e.target.value as Row["against"] })}>
              <option value="number">a number</option>
              {FIELDS.filter((f) => f.id !== r.field).map((f) => <option key={f.id} value={f.id}>{f.label}</option>)}
            </select>
            {r.against === "number" && (
              <input aria-label="Value" type="number" step="any" value={r.value} onChange={(e) => update(i, { value: e.target.value })} />
            )}
            <button className="btn btn-small" aria-label="Remove filter" onClick={() => setRows(rows.filter((_, j) => j !== i))}>Remove</button>
          </div>
        ))}
        <div className="row">
          <button className="btn" onClick={() => setRows([...rows, { field: "PRICE", op: "GT", against: "number", value: "" }])}>
            Add filter
          </button>
          <button className="btn btn-primary" onClick={run} disabled={busy || incomplete}>
            {busy ? "Running…" : "Run screen"}
          </button>
          {incomplete && <span className="muted small">Enter a number for every filter that compares with one.</span>}
        </div>
        {error && <div className="notice notice-error" role="alert">{error}</div>}
      </div>

      {result && (
        <>
          <h2>
            {result.totalMatches} of {result.universeSize} stocks match
            {result.totalMatches > result.matches.length && (
              <span className="muted small"> · showing the first {result.matches.length}</span>
            )}
            {result.unavailable > 0 && (
              <span className="muted small"> · {result.unavailable} could not be checked (no price data loaded yet)</span>
            )}
          </h2>
          {result.matches.length === 0 ? (
            <p className="muted">Nothing matches. Loosen or remove a filter and run it again.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Symbol</th><th>Name</th><th className="r">Price</th><th className="r">Change</th>
                    <th className="r">PE</th><th className="r">Vol vs avg</th><th className="r">50-day avg</th><th />
                  </tr>
                </thead>
                <tbody>
                  {result.matches.map((m) => (
                    <tr key={m.symbol}>
                      <td><button className="link sym" onClick={() => props.onOpen(m.symbol)}>{m.symbol}</button></td>
                      <td>{m.name}</td>
                      <td className="r">{fmt.money(m.price)}</td>
                      <td className={"r " + (m.changePct != null && m.changePct < 0 ? "down" : "up")}>{fmt.pct(m.changePct)}</td>
                      <td className="r">{fmt.num(m.pe)}</td>
                      <td className="r">{m.volume != null && m.avgVolume20d ? (m.volume / m.avgVolume20d).toFixed(2) + "×" : "–"}</td>
                      <td className="r">{fmt.money(m.sma50)}</td>
                      <td className="r"><button className="btn btn-small" onClick={() => props.onTrade({ symbol: m.symbol, side: "BUY" })}>Buy</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {result.matches[0]?.quote.source === "SIMULATED" && (
            <p className="muted small">Prices and fundamentals are simulated sample data, not real market data.</p>
          )}
        </>
      )}
    </section>
  );
}
