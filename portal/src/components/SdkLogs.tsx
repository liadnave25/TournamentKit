// The tournament's SDK-call feed (newest first): every /v1 call, success or failure.
// On failure it shows the error + the request payload so the developer can inspect the rejected input.
import type { SdkLogEntry } from "../lib/types";
import { Empty } from "./StateBlock";

// Outcome drives the rail/label color so failures jump out.
const OUTCOME_COLOR: Record<string, string> = {
  SUCCESS: "var(--tk-winner)",
  FAILURE: "var(--tk-danger)",
};

export function SdkLogs({ entries }: { entries: SdkLogEntry[] }) {
  if (entries.length === 0) return <Empty label="No SDK calls yet." />;

  return (
    <div style={{ display: "grid", gap: 10 }}>
      {entries.map((e, i) => {
        const color = OUTCOME_COLOR[e.outcome] ?? "var(--tk-line)";
        return (
          <div key={i} className="tk-card" style={{ padding: 14, display: "flex", gap: 14 }}>
            {/* Colored outcome rail. */}
            <span style={{ width: 4, borderRadius: 2, alignSelf: "stretch", background: color }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span className="tk-label" style={{ color }}>{e.action.replace(/_/g, " ")}</span>
                <span style={{ color, fontSize: 12 }}>{e.outcome}</span>
                <span style={{ color: "var(--tk-muted)", fontSize: 12 }}>{formatTime(e.timestamp)}</span>
              </div>

              {e.matchId && (
                <div style={{ marginTop: 6, fontSize: 14 }}>
                  <span style={{ color: "var(--tk-muted)" }}>match </span>
                  <span className="tk-num">{e.matchId}</span>
                </div>
              )}

              {/* Failure detail: error code/message + the rejected request payload. */}
              {e.outcome === "FAILURE" && (
                <>
                  {(e.errorCode || e.errorMessage) && (
                    <div style={{ marginTop: 6, fontSize: 14, color: "var(--tk-danger)" }}>
                      {e.errorCode && <span className="tk-num">{e.errorCode}</span>}
                      {e.errorMessage && <span style={{ color: "var(--tk-muted)" }}> · {e.errorMessage}</span>}
                    </div>
                  )}
                  {e.payload && Object.keys(e.payload).length > 0 && (
                    <div className="tk-num" style={{ marginTop: 6, fontSize: 12, color: "var(--tk-muted)", wordBreak: "break-all" }}>
                      {JSON.stringify(e.payload)}
                    </div>
                  )}
                </>
              )}

              {e.userId && (
                <div style={{ marginTop: 6, color: "var(--tk-muted)", fontSize: 12 }}>by {e.userId}</div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

// Formats an epoch-millis timestamp as a local date-time, or a dash.
function formatTime(ms?: number): string {
  if (!ms) return "—";
  return new Date(ms).toLocaleString();
}
