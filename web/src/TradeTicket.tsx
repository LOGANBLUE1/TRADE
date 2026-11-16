import { useEffect, useRef, useState } from "react";
import { api, ApiError, fmt, type Order, type Portfolio, type Quote } from "./api";
import type { Ticket } from "./App";

export function TradeTicket(props: {
  initial: Ticket;
  portfolio: Portfolio | null;
  onClose: () => void;
  onFilled: () => void;
}) {
  const [symbol, setSymbol] = useState(props.initial.symbol);
  const [side, setSide] = useState(props.initial.side);
  const [qty, setQty] = useState("1");
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [filled, setFilled] = useState<Order | null>(null);
  // One key per intended order: a retry after a network failure reuses it, so it can never fill twice.
  const key = useRef(crypto.randomUUID());

  useEffect(() => {
    const s = symbol.trim();
    setQuote(null);
    setQuoteError(null);
    if (s.length < 2) return;
    let stale = false;
    const timer = setTimeout(() => {
      api.quote(s)
        .then((q) => { if (!stale) setQuote(q); })
        .catch((e) => { if (!stale) setQuoteError(e instanceof ApiError ? e.message : "No quote available."); });
    }, 300);
    return () => { stale = true; clearTimeout(timer); };
  }, [symbol]);

  const quantity = Number(qty);
  const validQty = Number.isInteger(quantity) && quantity > 0;
  const estimate = quote && validQty ? quote.price * quantity : null;
  const held = props.portfolio?.positions.find((p) => p.symbol === quote?.symbol)?.quantity ?? 0;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!quote || !validQty || busy) return;
    setBusy(true);
    setError(null);
    try {
      setFilled(await api.placeOrder(quote.symbol, side, quantity, key.current));
      props.onFilled();
    } catch (err) {
      const apiErr = err instanceof ApiError ? err : null;
      setError(apiErr?.message ?? "The order could not be placed.");
      // The server rejected it, so nothing was created: the next attempt is a new order.
      if (apiErr && apiErr.code !== "NETWORK") key.current = crypto.randomUUID();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="overlay" onClick={props.onClose}>
      <div className="dialog" role="dialog" aria-modal="true" aria-label="Place an order" onClick={(e) => e.stopPropagation()}>
        {filled ? (
          <>
            <h2>Order filled</h2>
            <p>
              {filled.side === "BUY" ? "Bought" : "Sold"} {filled.quantity} {filled.symbol} at {fmt.money(filled.fillPrice)}.
            </p>
            <div className="dialog-actions">
              <button className="btn btn-primary" onClick={props.onClose}>Done</button>
            </div>
          </>
        ) : (
          <form onSubmit={submit}>
            <h2>Place an order</h2>
            <div className="segmented" role="group" aria-label="Buy or sell">
              <button type="button" className={side === "BUY" ? "seg seg-buy" : "seg"} onClick={() => setSide("BUY")}>Buy</button>
              <button type="button" className={side === "SELL" ? "seg seg-sell" : "seg"} onClick={() => setSide("SELL")}>Sell</button>
            </div>

            <label>
              Symbol
              <input autoFocus value={symbol} onChange={(e) => setSymbol(e.target.value)} placeholder="e.g. RELIANCE or TCS.NS" />
            </label>
            <div className="quote-line">
              {quote && (
                <>
                  <strong>{quote.name}</strong> · {fmt.price(quote.price, quote.currency)}{" "}
                  <span className={quote.changePct != null && quote.changePct < 0 ? "down" : "up"}>{fmt.pct(quote.changePct)}</span>
                  <span className="muted"> · {quote.source === "SIMULATED" ? "simulated price" : quote.delayed ? "delayed price" : "live"}</span>
                </>
              )}
              {quoteError && <span className="down">{quoteError}</span>}
              {!quote && !quoteError && <span className="muted">Type a symbol to see its price.</span>}
            </div>

            <label>
              Quantity
              <input type="number" min={1} step={1} value={qty} onChange={(e) => setQty(e.target.value)} />
            </label>
            {side === "SELL" && quote && <div className="muted small">You hold {held}.</div>}

            <div className="summary">
              <span>Estimated {side === "BUY" ? "cost" : "proceeds"}</span>
              <strong>{fmt.money(estimate)}</strong>
            </div>
            <div className="muted small">Market order, filled straight away at the price shown. Cash available: {fmt.money(props.portfolio?.cash)}.</div>

            {error && <div className="notice notice-error" role="alert">{error}</div>}

            <div className="dialog-actions">
              <button type="button" className="btn" onClick={props.onClose}>Cancel</button>
              <button type="submit" className={side === "BUY" ? "btn btn-buy" : "btn btn-sell"} disabled={!quote || !validQty || busy}>
                {busy ? "Placing…" : `${side === "BUY" ? "Buy" : "Sell"} ${validQty ? quantity : ""} ${quote?.symbol ?? ""}`}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
