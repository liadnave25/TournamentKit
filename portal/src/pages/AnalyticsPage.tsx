// Analytics: the headline numbers plus a small by-status bar breakdown. Light, mono numerals.
import { api } from "../lib/api";
import { usePid } from "../lib/usePid";
import { useAsync } from "../lib/useAsync";
import { Loading, ErrorState } from "../components/StateBlock";
import type { Analytics } from "../lib/types";

const STATUS_ORDER = ["REGISTRATION", "ACTIVE", "FROZEN", "FINISHED"];
const STATUS_COLOR: Record<string, string> = {
  REGISTRATION: "var(--tk-muted)",
  ACTIVE: "var(--tk-info)",
  FROZEN: "var(--tk-primary)",
  FINISHED: "var(--tk-winner)",
};

export function AnalyticsPage() {
  const pid = usePid();
  const { data, loading, error, reload } = useAsync<Analytics>(() => api.analytics(pid), [pid]);

  return (
    <div style={{ maxWidth: 920, margin: "0 auto", padding: "32px 22px" }}>
      <h1 className="tk-display" style={{ fontSize: 26, margin: "0 0 20px" }}>Analytics</h1>

      {loading && <Loading label="Loading analytics…" />}
      {error && <ErrorState message={error.message} onRetry={reload} />}

      {data && (
        <>
          <div style={{ display: "grid", gap: 16, gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))" }}>
            <Stat label="Tournaments" value={data.tournamentsTotal} accent="primary" />
            <Stat label="Participants" value={data.participantsTotal} />
            <Stat label="Matches confirmed" value={data.matchesConfirmed} accent="winner" />
            <Stat label="Last created" value={formatDate(data.lastTournamentCreatedAt)} small />
          </div>

          <div className="tk-label" style={{ marginTop: 32, marginBottom: 12 }}>Tournaments by status</div>
          <StatusBars byStatus={data.tournamentsByStatus} total={data.tournamentsTotal} />
        </>
      )}
    </div>
  );
}

// A headline metric card.
function Stat({ label, value, accent, small }: { label: string; value: number | string; accent?: "primary" | "winner"; small?: boolean }) {
  const color = accent === "primary" ? "var(--tk-primary)" : accent === "winner" ? "var(--tk-winner)" : "var(--tk-on-surface)";
  return (
    <div className="tk-card" style={{ padding: 20 }}>
      <div className="tk-label">{label}</div>
      <div className={small ? "tk-display" : "tk-num"} style={{ marginTop: 10, fontSize: small ? 18 : 38, color, lineHeight: 1.1 }}>
        {value}
      </div>
    </div>
  );
}

// A horizontal bar per status, scaled to the largest count.
function StatusBars({ byStatus, total }: { byStatus: Record<string, number>; total: number }) {
  if (total === 0) {
    return (
      <div className="tk-card" style={{ padding: 20, color: "var(--tk-muted)" }}>
        No tournaments yet — they'll appear here once your app creates some.
      </div>
    );
  }
  const max = Math.max(1, ...STATUS_ORDER.map((s) => byStatus[s] ?? 0));
  return (
    <div className="tk-card" style={{ padding: 18, display: "grid", gap: 12 }}>
      {STATUS_ORDER.map((s) => {
        const n = byStatus[s] ?? 0;
        return (
          <div key={s} style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <span className="tk-label" style={{ width: 110, color: STATUS_COLOR[s] }}>{s}</span>
            <div style={{ flex: 1, height: 10, background: "var(--tk-surface)", borderRadius: 999, overflow: "hidden" }}>
              <div style={{ width: `${(n / max) * 100}%`, height: "100%", background: STATUS_COLOR[s], borderRadius: 999 }} />
            </div>
            <span className="tk-num" style={{ width: 36, textAlign: "right", fontSize: 15 }}>{n}</span>
          </div>
        );
      })}
    </div>
  );
}

// Formats an epoch-millis timestamp, or a dash.
function formatDate(ms: number | null): string {
  if (!ms) return "—";
  return new Date(ms).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}
