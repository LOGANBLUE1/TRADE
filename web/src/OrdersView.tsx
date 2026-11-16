import { useState } from "react";
import { api, ApiError, fmt, type Order, type OrderStatus } from "./api";

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_APPROVAL: "Waiting for you",
  APPROVED: "Approved, not filled yet",
  FILLED: "Filled",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  EXPIRED: "Approval expired",
};

type Action = "approve" | "reject" | "cancel" | "execute";

export function OrdersView(props: { orders: Order[]; onChanged: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function act(order: Order, action: Action) {
    setBusy(order.id);
    setError(null);
    try {
      await api.orderAction(order.id, action);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "That did not work. Try again.");
    } finally {
      setBusy(null);
      props.onChanged();
    }
  }

  const waiting = props.orders.filter((o) => o.status === "PENDING_APPROVAL");
  const approved = props.orders.filter((o) => o.status === "APPROVED");
  const history = props.orders.filter((o) => o.status !== "PENDING_APPROVAL" && o.status !== "APPROVED");

  return (
    <section>
      {error && <div className="notice notice-error" role="alert">{error}</div>}

      <h2>Waiting for your approval</h2>
      {waiting.length === 0 ? (
        <p className="muted">Nothing is waiting. Trades proposed by the AI assistant appear here for you to approve or reject.</p>
      ) : (
        <div className="approvals">
          {waiting.map((o) => (
            <div className="approval" key={o.id}>
              <div>
                <div className="approval-title">
                  <span className={o.side === "BUY" ? "up" : "down"}>{o.side === "BUY" ? "Buy" : "Sell"}</span>{" "}
                  {o.quantity} {o.symbol} <span className="muted">at about {fmt.money(o.referencePrice)}</span>
                </div>
                <div className="muted small">
                  About {fmt.money(o.referencePrice * o.quantity)} · proposed by {o.source === "AI" ? "the AI assistant" : "you"} · {fmt.time(o.createdAt)}
                </div>
                {/* Text written by the model: shown as plain text only. */}
                {o.rationale && <div className="rationale">AI's reason: {o.rationale}</div>}
              </div>
              <div className="actions">
                <button className="btn" disabled={busy === o.id} onClick={() => act(o, "reject")}>Reject</button>
                <button className="btn btn-primary" disabled={busy === o.id} onClick={() => act(o, "approve")}>Approve</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {approved.length > 0 && (
        <>
          <h2>Approved, not filled yet</h2>
          <p className="muted small">The assistant can now fill these, or you can. Approvals expire after 15 minutes.</p>
          <div className="approvals">
            {approved.map((o) => (
              <div className="approval" key={o.id}>
                <div className="approval-title">
                  {o.side === "BUY" ? "Buy" : "Sell"} {o.quantity} {o.symbol}{" "}
                  <span className="muted">at about {fmt.money(o.referencePrice)}</span>
                </div>
                <div className="actions">
                  <button className="btn" disabled={busy === o.id} onClick={() => act(o, "cancel")}>Cancel order</button>
                  <button className="btn btn-primary" disabled={busy === o.id} onClick={() => act(o, "execute")}>Fill now</button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <h2>History</h2>
      {history.length === 0 ? (
        <p className="muted">No orders yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Placed</th><th>Order</th><th className="r">Qty</th><th className="r">Fill price</th>
                <th>Status</th><th>From</th>
              </tr>
            </thead>
            <tbody>
              {history.map((o) => (
                <tr key={o.id}>
                  <td>{fmt.time(o.createdAt)}</td>
                  <td><span className={o.side === "BUY" ? "up" : "down"}>{o.side === "BUY" ? "Buy" : "Sell"}</span> <span className="sym">{o.symbol}</span></td>
                  <td className="r">{fmt.num(o.quantity)}</td>
                  <td className="r">{fmt.money(o.fillPrice)}</td>
                  <td>{STATUS_LABEL[o.status]}</td>
                  <td>{o.source === "AI" ? "AI assistant" : "You"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
