// A tournament-status pill using the Floodlight state colors. Reused by lists and the detail header.
import type { TournamentStatus } from "../lib/types";

const COLORS: Record<TournamentStatus, string> = {
  REGISTRATION: "var(--tk-muted)",
  ACTIVE: "var(--tk-info)",      // in-progress = cyan
  FROZEN: "var(--tk-primary)",   // paused by an admin = gold
  FINISHED: "var(--tk-winner)",  // done = green
};

// Renders a colored status pill for a tournament status.
export function StatusChip({ status }: { status: TournamentStatus }) {
  const color = COLORS[status] ?? "var(--tk-muted)";
  return (
    <span className="tk-pill" style={{ color }}>
      <span className="tk-dot" style={{ background: color }} /> {status}
    </span>
  );
}
