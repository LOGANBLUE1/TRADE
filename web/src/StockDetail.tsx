import { useEffect, useState } from "react";
import { api, ApiError, fmt, type Instrument, type Portfolio } from "./api";
import type { Ticket } from "./App";

const REFRESH_MS = 15000;

export function StockDetail(props: {
  symbol: string;
  portfolio: Portfolio | null;
  onBack: () => void;
  onTrade: (t: Ticket) => void;
}) {
  const [item, setItem] = useState<Instrument | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let stale = false;
    const load = () =>
      api.instrument(props.symbol)
        .then((r) => { if (!stale) { setItem(r); setError(null); } })
        .catch((e) => { if (!stale) setError(e instanceof ApiError ? e.message : "Details could not be loaded."); });
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => { stale = true; clearInterval(timer); };
  }, [props.symbol]);

  const back = <button className="link" onClick={props.onBack}>← Back</button>;
  if (error && !item) return <section>{back}<div className="notice notice-error" role="alert">{error}</div></section>;
  if (!item) return <section>{back}<p className="muted">Loading {props.symbol}…</p></section>;

  const s = item.data;
  const q = s.quote;
  const isFx = item.type === "FX";
  const price = (v: number | null) => (isFx ? fmt.num4(v) : fmt.price(v, q.currency));
  const held = props.portfolio?.positions.find((p) => p.symbol === s.symbol);
  const volRatio = s.volume != null && s.avgVolume20d ? s.volume / s.avgVolume20d : null;
  const vs = (avg: number | null) => (avg ? ((s.price - avg) / avg) * 100 : null);

  return (
    <section>
      {back}
      {error && <div className="notice notice-error" role="alert">{error} Showing the last data loaded.</div>}

      <div className="detail-head">
        <div>
          <h1>{s.name}</h1>
          <div className="muted">{s.symbol} · {isFx ? "Currency pair" : "NSE stock"}</div>
        </div>
        <div className="detail-price">
          <div className="detail-price-value">{price(s.price)}{isFx && <span className="muted small"> {q.currency}</span>}</div>
          <div className={s.changePct != null && s.changePct < 0 ? "down" : "up"}>
            {fmt.pct(s.changePct)} today
          </div>
        </div>
      </div>
      <div className="muted small">
        {q.source === "SIMULATED" ? "Simulated sample data, not a real market price" : q.delayed ? "Delayed by about 15 minutes" : "Live"}
        {" · as of "}{fmt.time(q.asOf)}
      </div>

      {item.tradable ? (
        <div className="row detail-actions">
          <button className="btn btn-buy" onClick={() => props.onTrade({ symbol: s.symbol, side: "BUY" })}>Buy</button>
          <button className="btn btn-sell" disabled={!held} onClick={() => props.onTrade({ symbol: s.symbol, side: "SELL" })}>Sell</button>
          {!held && <span className="muted small">You don't hold this stock.</span>}
        </div>
      ) : (
        <p className="muted small">Prices only. This cannot be traded on the paper account yet.</p>
      )}

      {held && (
        <>
          <h2>Your holding</h2>
          <div className="facts">
            <Fact label="Quantity" value={fmt.num(held.quantity)} />
            <Fact label="Average cost" value={fmt.money(held.avgCost)} />
            <Fact label="Current value" value={fmt.money(held.marketValue)} />
            <Fact label="Gain/loss" value={fmt.money(held.unrealizedPnl)}
                  tone={held.unrealizedPnl == null || held.unrealizedPnl === 0 ? undefined : held.unrealizedPnl > 0 ? "up" : "down"} />
          </div>
        </>
      )}

      <h2>Price</h2>
      <div className="facts">
        <Fact label="Previous close" value={price(q.previousClose)} />
        <Fact label="50-day average" value={price(s.sma50)} note={against(vs(s.sma50))} />
        <Fact label="200-day average" value={price(s.sma200)} note={against(vs(s.sma200))} />
      </div>

      {!isFx && (
        <>
          <h2>Fundamentals and volume</h2>
          <div className="facts">
            <Fact label="PE ratio" value={fmt.num(s.pe)} />
            <Fact label="Market cap" value={s.marketCapCr == null ? "–" : `₹${fmt.num(s.marketCapCr)} cr`} />
            <Fact label="Volume today" value={fmt.num(s.volume)} />
            <Fact label="Average volume (20 days)" value={fmt.num(s.avgVolume20d)}
                  note={volRatio == null ? undefined : `Today is ${volRatio.toFixed(2)}× the average`} />
          </div>
          {(s.pe == null || s.marketCapCr == null) && (
            <p className="muted small">“–” means the current price source does not provide that figure.</p>
          )}
        </>
      )}
    </section>
  );
}

const against = (pct: number | null) =>
  pct == null ? undefined : `Price is ${Math.abs(pct).toFixed(1)}% ${pct >= 0 ? "above" : "below"}`;

function Fact(props: { label: string; value: string; note?: string; tone?: "up" | "down" }) {
  return (
    <div className="fact">
      <div className="stat-label">{props.label}</div>
      <div className={"fact-value " + (props.tone ?? "")}>{props.value}</div>
      {props.note && <div className="muted small">{props.note}</div>}
    </div>
  );
}
