// The dashboard: headline analytics for the selected project in a bento layout (ported from the
// Stitch "Analytics Overview"), handling loading, TKError, and a brand-new project. All numbers are
// the project's real analytics (GET /analytics) — no fabricated telemetry.
import { Link } from "react-router-dom";
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
    <div style={{ padding: 24 }}>
      {/* Page header. */}
      <div style={{ marginBottom: 28 }}>
        <h1 className="tk-display" style={{ fontSize: 24, margin: "0 0 6px" }}>Analytics Overview</h1>
        <p style={{ color: "var(--tk-muted)", margin: 0, fontSize: 14 }}>
          Real-time metrics and active tournament statuses for{" "}
          <span style={{ color: "var(--tk-on-surface)", fontWeight: 600 }}>{project?.name ?? "your project"}</span>{" "}
          <span className="tk-num" style={{ fontSize: 12, color: "var(--tk-muted)" }}>({pid})</span>.
        </p>
      </div>

      {loading && <Loading label="Loading analytics…" />}
      {error && <ErrorState message={error.message} onRetry={reload} />}

      {data && (
        <div className="tk-bento">
          {/* Large card: platform activity headline numbers. */}
          <div className="tk-card tk-bento-large" style={{ padding: 24, display: "flex", flexDirection: "column" }}>
            <CardHead icon="monitoring" title="Platform Activity">
              <span className="tk-pill" style={{ color: "var(--tk-primary)", background: "var(--tk-surface-3)" }}>
                <span className="tk-dot" style={{ background: "var(--tk-primary)" }} /> Live
              </span>
            </CardHead>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 32, flex: 1, marginTop: 28 }}>
              <BigStat label="Total Tournaments" value={data.tournamentsTotal} />
              <div style={{ borderLeft: "1px solid var(--tk-line)", paddingLeft: 32 }}>
                <BigStat label="Total Participants" value={data.participantsTotal} />
              </div>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 32, marginTop: 28, paddingTop: 22, borderTop: "1px solid var(--tk-line)" }}>
              <div>
                <div className="tk-label">Matches Confirmed</div>
                <div className="tk-num" style={{ fontSize: 22, marginTop: 6 }}>{data.matchesConfirmed.toLocaleString()}</div>
              </div>
              <div>
                <div className="tk-label">Last Created</div>
                <div
                  className="tk-num"
                  style={{ display: "inline-block", marginTop: 6, fontSize: 13, padding: "4px 8px", borderRadius: 4, background: "var(--tk-surface-3)" }}
                >
                  {formatDateTime(data.lastTournamentCreatedAt)}
                </div>
              </div>
            </div>
          </div>

          {/* By-status breakdown with a segmented proportion bar. */}
          <div className="tk-card" style={{ padding: 24, display: "flex", flexDirection: "column" }}>
            <CardHead icon="donut_large" title="By Status" />
            <StatusBreakdown byStatus={data.tournamentsByStatus} total={data.tournamentsTotal} />
          </div>

          {/* Quick links into the project (replaces the mock's fabricated System Health telemetry). */}
          <div className="tk-card" style={{ padding: 24, display: "flex", flexDirection: "column" }}>
            <CardHead icon="bolt" title="Quick Links" />
            <div style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 18, flex: 1 }}>
              <QuickLink to={`/projects/${pid}/tournaments`} icon="emoji_events" label="Tournaments" />
              <QuickLink to={`/projects/${pid}/templates`} icon="description" label="Templates" />
              <QuickLink to={`/projects/${pid}/keys`} icon="vpn_key" label="API Keys" />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Card header: a leading icon, a title, and optional trailing content (e.g. a Live pill).
function CardHead({ icon, title, children }: { icon: string; title: string; children?: React.ReactNode }) {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
      <h3 className="tk-display" style={{ fontSize: 18, margin: 0, display: "flex", alignItems: "center", gap: 8 }}>
        <span className="material-symbols-outlined" style={{ color: "var(--tk-primary)", fontSize: 22 }} aria-hidden>{icon}</span>
        {title}
      </h3>
      {children}
    </div>
  );
}

// One oversized headline metric (label above, big mono number below).
function BigStat({ label, value }: { label: string; value: number }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", justifyContent: "center" }}>
      <div className="tk-label" style={{ marginBottom: 8 }}>{label}</div>
      <div className="tk-num" style={{ fontSize: 44, lineHeight: 1, color: "var(--tk-on-surface)" }}>
        {value.toLocaleString()}
      </div>
    </div>
  );
}

// A row link into a project section.
function QuickLink({ to, icon, label }: { to: string; icon: string; label: string }) {
  return (
    <Link
      to={to}
      className="tk-btn tk-btn-ghost"
      style={{ justifyContent: "flex-start", textDecoration: "none", padding: "11px 12px", background: "var(--tk-surface-3)" }}
    >
      <span className="material-symbols-outlined" style={{ color: "var(--tk-primary)", fontSize: 20 }} aria-hidden>{icon}</span>
      {label}
      <span style={{ flex: 1 }} />
      <span className="material-symbols-outlined" style={{ color: "var(--tk-muted)", fontSize: 18 }} aria-hidden>chevron_right</span>
    </Link>
  );
}

// A per-status list with colored dots + a segmented proportion bar; reads cleanly on a fresh project.
function StatusBreakdown({ byStatus, total }: { byStatus: Record<string, number>; total: number }) {
  const order = ["REGISTRATION", "ACTIVE", "FROZEN", "FINISHED"];
  const colorFor: Record<string, string> = {
    REGISTRATION: "#3b82f6", // blue
    ACTIVE: "#22c55e",       // green
    FROZEN: "#f59e0b",       // amber
    FINISHED: "#9ca3af",     // gray
  };
  if (total === 0) {
    return (
      <div style={{ marginTop: 18, color: "var(--tk-muted)", fontSize: 14 }}>
        No tournaments yet — they'll appear here once your app creates some.
      </div>
    );
  }
  return (
    <>
      <div style={{ display: "flex", flexDirection: "column", gap: 14, marginTop: 18, flex: 1, justifyContent: "center" }}>
        {order.map((s) => (
          <div key={s} style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span style={{ width: 12, height: 12, borderRadius: "50%", background: colorFor[s] }} />
              <span className="tk-pill" style={{ background: "var(--tk-surface-3)", color: "var(--tk-on-surface)" }}>{s}</span>
            </span>
            <span className="tk-num" style={{ fontSize: 14, fontWeight: 700 }}>{byStatus[s] ?? 0}</span>
          </div>
        ))}
      </div>
      {/* Segmented proportion bar. */}
      <div style={{ marginTop: 20, paddingTop: 16, borderTop: "1px solid var(--tk-line)" }}>
        <div className="tk-track" style={{ display: "flex" }}>
          {order.map((s) => {
            const pct = total > 0 ? ((byStatus[s] ?? 0) / total) * 100 : 0;
            return pct > 0 ? <div key={s} style={{ width: `${pct}%`, background: colorFor[s] }} /> : null;
          })}
        </div>
      </div>
    </>
  );
}

// Formats an epoch-millis timestamp as "YYYY-MM-DD HH:MM UTC", or a dash when there's nothing yet.
function formatDateTime(ms: number | null): string {
  if (!ms) return "—";
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())} UTC`;
}
