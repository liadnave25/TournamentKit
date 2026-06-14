// The dashboard: headline analytics for the selected project. Handles loading, error (TKError message),
// and a brand-new project (all zeros) gracefully.
import { api } from "../lib/api";
import { useProjects } from "../lib/project";
import { usePid } from "../lib/usePid";
import { useAsync } from "../lib/useAsync";
import { Loading, ErrorState } from "../components/StateBlock";
import type { Analytics } from "../lib/types";

export function DashboardPage() {
  const pid = usePid();
  const { projects } = useProjects();
  const project = projects.find((p) => p.id === pid);

  // Fetch analytics whenever the routed project changes.
  const { data, loading, error, reload } = useAsync<Analytics>(() => api.analytics(pid), [pid]);

  return (
    <div style={{ maxWidth: 1080, margin: "0 auto", padding: "32px 22px" }}>
      <div style={{ display: "flex", alignItems: "baseline", gap: 12, marginBottom: 4 }}>
        <h1 className="tk-display" style={{ fontSize: 28, margin: 0 }}>{project?.name ?? "Project"}</h1>
        <span className="tk-num" style={{ color: "var(--tk-muted)", fontSize: 13 }}>{pid}</span>
      </div>
      <div className="tk-label" style={{ marginBottom: 24 }}>Dashboard</div>

      {loading && <Loading label="Loading analytics…" />}
      {error && <ErrorState message={error.message} onRetry={reload} />}

      {data && (
        <>
          {/* Headline number cards. */}
          <div
            style={{
              display: "grid", gap: 16,
              gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))",
            }}
          >
            <StatCard label="Tournaments" value={data.tournamentsTotal} accent="primary" />
            <StatCard label="Participants" value={data.participantsTotal} />
            <StatCard label="Matches confirmed" value={data.matchesConfirmed} accent="winner" />
            <StatCard
              label="Last created"
              value={formatDate(data.lastTournamentCreatedAt)}
              small
            />
          </div>

          {/* By-status breakdown. */}
          <div className="tk-label" style={{ marginTop: 32, marginBottom: 12 }}>By status</div>
          <StatusBreakdown byStatus={data.tournamentsByStatus} total={data.tournamentsTotal} />
        </>
      )}
    </div>
  );
}

// A single headline metric card; the accent tints the number (gold / green by default).
function StatCard({
  label, value, accent, small,
}: { label: string; value: number | string; accent?: "primary" | "winner"; small?: boolean }) {
  const color =
    accent === "primary" ? "var(--tk-primary)" : accent === "winner" ? "var(--tk-winner)" : "var(--tk-on-surface)";
  return (
    <div className="tk-card" style={{ padding: 20 }}>
      <div className="tk-label">{label}</div>
      <div
        className={small ? "tk-display" : "tk-num"}
        style={{ marginTop: 10, fontSize: small ? 18 : 40, color, lineHeight: 1.1 }}
      >
        {value}
      </div>
    </div>
  );
}

// A compact per-status list with colored dots; "0 tournaments" reads cleanly on a fresh project.
function StatusBreakdown({ byStatus, total }: { byStatus: Record<string, number>; total: number }) {
  const order = ["REGISTRATION", "ACTIVE", "FROZEN", "FINISHED"];
  const colorFor: Record<string, string> = {
    REGISTRATION: "var(--tk-muted)",
    ACTIVE: "var(--tk-info)",
    FROZEN: "var(--tk-primary)",
    FINISHED: "var(--tk-winner)",
  };
  if (total === 0) {
    return (
      <div className="tk-card" style={{ padding: 20, color: "var(--tk-muted)" }}>
        No tournaments yet — they'll appear here once your app creates some.
      </div>
    );
  }
  return (
    <div className="tk-card" style={{ padding: 8 }}>
      {order
        .filter((s) => (byStatus[s] ?? 0) > 0)
        .map((s) => (
          <div
            key={s}
            style={{
              display: "flex", alignItems: "center", justifyContent: "space-between",
              padding: "12px 14px", borderBottom: "1px solid var(--tk-line)",
            }}
          >
            <span className="tk-pill" style={{ color: colorFor[s] }}>
              <span className="tk-dot" /> {s}
            </span>
            <span className="tk-num" style={{ fontSize: 18 }}>{byStatus[s]}</span>
          </div>
        ))}
    </div>
  );
}

// Formats an epoch-millis timestamp, or a dash when there's nothing yet.
function formatDate(ms: number | null): string {
  if (!ms) return "—";
  return new Date(ms).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}
