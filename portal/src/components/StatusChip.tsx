// A tournament-status pill with a high-contrast tinted background + dark text, per the Stitch design
// (light bg, darker text of the same hue). Reused by lists and the detail header.
import type { TournamentStatus } from "../lib/types";

// Each status: dot/text color + a soft background fill.
const STYLES: Record<TournamentStatus, { fg: string; bg: string; border: string }> = {
  REGISTRATION: { fg: "#1d4ed8", bg: "#eff6ff", border: "#bfdbfe" }, // blue
  ACTIVE:       { fg: "#166534", bg: "#dcfce7", border: "#bbf7d0" }, // green
  FROZEN:       { fg: "#b45309", bg: "#fef3c7", border: "#fde68a" }, // amber
  FINISHED:     { fg: "#374151", bg: "#f3f4f6", border: "#d1d5db" }, // gray
};

// Renders a colored status pill for a tournament status.
export function StatusChip({ status }: { status: TournamentStatus }) {
  const s = STYLES[status] ?? STYLES.FINISHED;
  return (
    <span className="tk-pill" style={{ color: s.fg, background: s.bg, borderColor: s.border }}>
      <span className="tk-dot" style={{ background: s.fg }} /> {status}
    </span>
  );
}
