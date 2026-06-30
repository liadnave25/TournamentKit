// Tournaments list (newest first) with an optional status filter. Rows link to the visual detail.
import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { usePid } from "../lib/usePid";
import { useAsync } from "../lib/useAsync";
import { Loading, Empty, ErrorState } from "../components/StateBlock";
import { StatusChip } from "../components/StatusChip";
import type { TournamentStatus, TournamentSummary } from "../lib/types";

const FILTERS: ("ALL" | TournamentStatus)[] = ["ALL", "REGISTRATION", "ACTIVE", "FROZEN", "FINISHED"];

export function TournamentsPage() {
  const pid = usePid();
  const [filter, setFilter] = useState<"ALL" | TournamentStatus>("ALL");

  // Re-fetch when the project or the status filter changes.
  const { data, loading, error, reload } = useAsync<TournamentSummary[]>(
    () => api.listTournaments(pid, filter === "ALL" ? undefined : filter),
    [pid, filter]
  );

  return (
    <div style={{ maxWidth: 980, padding: 24 }}>
      <div style={{ marginBottom: 18 }}>
        <h1 className="tk-display" style={{ fontSize: 24, margin: "0 0 4px" }}>Tournaments Overview</h1>
        <p style={{ color: "var(--tk-muted)", margin: 0, fontSize: 14 }}>
          Every tournament in this project. Your app creates these via the SDK.
        </p>
      </div>

      {/* Status filter chips. */}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 20 }}>
        {FILTERS.map((f) => (
          <button
            key={f}
            className={`tk-btn${filter === f ? " tk-btn-primary" : " tk-btn-ghost"}`}
            onClick={() => setFilter(f)}
            style={filter === f ? undefined : { background: "var(--tk-surface-3)" }}
          >
            {f}
          </button>
        ))}
      </div>

      {loading && <Loading label="Loading tournaments…" />}
      {error && <ErrorState message={error.message} onRetry={reload} />}
      {data && data.length === 0 && (
        <Empty label={filter === "ALL" ? "No tournaments yet — your app creates these via the SDK." : `No ${filter} tournaments.`} />
      )}

      {data && data.length > 0 && (
        <div style={{ display: "grid", gap: 10 }}>
          {data.map((t) => (
            <Link
              key={t.id}
              to={`/projects/${pid}/tournaments/${t.id}`}
              className="tk-card"
              style={{
                padding: 16, display: "flex", alignItems: "center", gap: 16,
                textDecoration: "none", color: "inherit",
              }}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="tk-display" style={{ fontSize: 17 }}>{t.name}</div>
                <div style={{ color: "var(--tk-muted)", fontSize: 13, marginTop: 3 }}>
                  {t.participantCount} participant{t.participantCount === 1 ? "" : "s"} · {formatDate(t.createdAt)}
                </div>
              </div>
              <StatusChip status={t.status} />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

// Formats an epoch-millis timestamp as a short date.
function formatDate(ms: number): string {
  return new Date(ms).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}
