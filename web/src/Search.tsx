import { useEffect, useRef, useState } from "react";
import { api, fmt, type Instrument } from "./api";

/** Top-bar search over stock symbols and company names. */
export function Search({ onOpen }: { onOpen: (symbol: string) => void }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<Instrument[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const term = q.trim();
    setResults(null);
    setFailed(false);
    setActive(0);
    if (!term) return;
    let stale = false;
    const timer = setTimeout(() => {
      api.searchInstruments(term, 8)
        .then((r) => { if (!stale) setResults(r); })
        .catch(() => { if (!stale) setFailed(true); });
    }, 200);
    return () => { stale = true; clearTimeout(timer); };
  }, [q]);

  useEffect(() => {
    const close = (e: MouseEvent) => { if (!box.current?.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  function choose(symbol: string) {
    onOpen(symbol);
    setQ("");
    setOpen(false);
  }

  function onKey(e: React.KeyboardEvent) {
    if (e.key === "Escape") setOpen(false);
    if (!results?.length) return;
    if (e.key === "ArrowDown") { e.preventDefault(); setActive((active + 1) % results.length); }
    if (e.key === "ArrowUp") { e.preventDefault(); setActive((active - 1 + results.length) % results.length); }
    if (e.key === "Enter") { e.preventDefault(); choose(results[active].symbol); }
  }

  const show = open && q.trim() !== "";
  return (
    <div className="search" ref={box}>
      <input type="search" aria-label="Search stocks" placeholder="Search NIFTY 500 by name, symbol or industry"
             value={q} onChange={(e) => { setQ(e.target.value); setOpen(true); }}
             onFocus={() => setOpen(true)} onKeyDown={onKey} />
      {show && (
        <div className="search-results">
          {failed && <div className="search-note">Search is not available right now.</div>}
          {!failed && results === null && <div className="search-note">Searching…</div>}
          {results?.length === 0 && (
            <div className="search-note">No stock matches “{q.trim()}”. Search covers the NIFTY 500 stocks and a few currency pairs.</div>
          )}
          {results?.map((r, i) => (
            <button key={r.symbol} className={i === active ? "search-item search-item-active" : "search-item"}
                    onMouseEnter={() => setActive(i)} onClick={() => choose(r.symbol)}>
              <span>
                <span className="sym">{r.symbol}</span> <span className="muted">{r.name}</span>
              </span>
              {r.data ? (
                <span>
                  {r.type === "FX" ? fmt.num4(r.data.price) : fmt.money(r.data.price)}{" "}
                  <span className={r.data.changePct != null && r.data.changePct < 0 ? "down" : "up"}>{fmt.pct(r.data.changePct)}</span>
                </span>
              ) : (
                <span className="muted">No price yet</span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
