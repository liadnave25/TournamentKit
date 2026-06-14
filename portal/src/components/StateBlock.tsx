// Reusable loading / empty / error blocks so every screen handles these states consistently.
import type { ReactNode } from "react";

// A quiet centered message used for empty/loading panels.
function Panel({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        padding: "40px 20px",
        textAlign: "center",
        color: "var(--tk-muted)",
        fontFamily: "var(--tk-font-body)",
      }}
      role="status"
    >
      {children}
    </div>
  );
}

// Shown while data is loading.
export function Loading({ label = "Loading…" }: { label?: string }) {
  return <Panel>{label}</Panel>;
}

// Shown when there is genuinely nothing to display.
export function Empty({ label }: { label: string }) {
  return <Panel>{label}</Panel>;
}

// Shown when a request failed; surfaces the server's TKError message and an optional retry.
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      style={{
        padding: "16px 18px",
        borderRadius: "var(--tk-radius-sm)",
        border: "1px solid var(--tk-line)",
        background: "rgba(255,93,93,0.08)",
        color: "var(--tk-on-surface)",
      }}
    >
      <div style={{ color: "var(--tk-danger)", fontWeight: 600, marginBottom: onRetry ? 10 : 0 }}>
        {message}
      </div>
      {onRetry && (
        <button className="tk-btn" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
