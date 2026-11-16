import { fmt, type AuditEvent } from "./api";

const EVENT_LABEL: Record<string, string> = {
  PROPOSED: "Proposed",
  APPROVED: "Approved",
  FILLED: "Filled",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  EXPIRED: "Approval expired",
};

const who = (actor: string) =>
  actor.startsWith("ai-for:") ? "AI assistant" : actor.startsWith("user:") ? "You" : "System";

export function ActivityView({ events }: { events: AuditEvent[] }) {
  return (
    <section>
      <h2>Activity</h2>
      <p className="muted small">Every proposal, approval and fill on your account, newest first.</p>
      {events.length === 0 ? (
        <p className="muted">Nothing has happened yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>When</th><th>What</th><th>By</th><th>Details</th><th>Order</th></tr>
            </thead>
            <tbody>
              {events.map((e) => (
                <tr key={e.id}>
                  <td>{fmt.time(e.at)}</td>
                  <td>{EVENT_LABEL[e.eventType] ?? e.eventType}</td>
                  <td>{who(e.actor)}</td>
                  <td>{e.detail ?? ""}</td>
                  <td className="muted mono">{e.orderId?.slice(0, 8) ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
