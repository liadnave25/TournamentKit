// The tournament audit timeline (newest first): action, who, when, and the reason / old→new score.
// This is the accountability story — override/freeze/unfreeze/key-rotation all show up here.
import type { AuditEntry } from "../lib/types";
import { Empty } from "./StateBlock";

// Color-codes each action so the timeline scans quickly.
const ACTION_COLOR: Record<string, string> = {
  OVERRIDE_RESULT: "var(--tk-primary)",
  FREEZE: "var(--tk-info)",
  UNFREEZE: "var(--tk-winner)",
  KEY_ROTATED: "var(--tk-muted)",
};

export function AuditLog({ entries }: { entries: AuditEntry[] }) {
  if (entries.length === 0) return <Empty label="No admin actions yet." />;

  return (
    <div style={{ display: "grid", gap: 10 }}>
      {entries.map((e, i) => (
        <div key={i} className="tk-card" style={{ padding: 14, display: "flex", gap: 14 }}>
          {/* Colored action rail. */}
          <span
            style={{
              width: 4, borderRadius: 2, alignSelf: "stretch",
              background: ACTION_COLOR[e.action] ?? "var(--tk-line)",
            }}
          />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span className="tk-label" style={{ color: ACTION_COLOR[e.action] ?? "var(--tk-muted)" }}>
                {e.action.replace(/_/g, " ")}
              </span>
              <span style={{ color: "var(--tk-muted)", fontSize: 12 }}>{formatTime(e.timestamp)}</span>
            </div>

            {/* Score change for overrides. */}
            {(e.oldScore || e.newScore) && (
              <div className="tk-num" style={{ marginTop: 6, fontSize: 14 }}>
                {e.matchId && <span style={{ color: "var(--tk-muted)" }}>{e.matchId}: </span>}
                {e.oldScore ? `${e.oldScore.home}–${e.oldScore.away}` : "—"}
                <span style={{ color: "var(--tk-muted)" }}> → </span>
                {e.newScore ? `${e.newScore.home}–${e.newScore.away}` : "—"}
              </div>
            )}

            {e.reason && (
              <div style={{ marginTop: 6, fontSize: 14 }}>
                <span style={{ color: "var(--tk-muted)" }}>Reason: </span>{e.reason}
              </div>
            )}

            {e.adminUid && (
              <div style={{ marginTop: 6, color: "var(--tk-muted)", fontSize: 12 }}>by {e.adminUid}</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

// Formats an epoch-millis timestamp as a local date-time, or a dash.
function formatTime(ms?: number): string {
  if (!ms) return "—";
  return new Date(ms).toLocaleString();
}
