// Admin result-override dialog requiring a score and a non-empty reason, surfacing the server's 409 rather than hiding it.
import { useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import { Dialog } from "./Dialog";
import type { Match, TournamentView } from "../lib/types";

export function OverrideDialog({
  pid, tid, match, nameOf, onClose, onApplied,
}: {
  pid: string;
  tid: string;
  match: Match;
  nameOf: (id: string) => string;
  onClose: () => void;
  onApplied: (view: TournamentView) => void;
}) {
  const [home, setHome] = useState(match.score?.home ?? 0);
  const [away, setAway] = useState(match.score?.away ?? 0);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const validScore = home >= 0 && away >= 0;
  const validReason = reason.trim().length > 0;

  // Posts the override; surfaces any TKError (validation / 409 propagation) verbatim.
  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!validScore || !validReason) return;
    setError(null);
    setBusy(true);
    try {
      const view = await api.overrideMatch(pid, tid, match.id, { home, away }, reason.trim());
      onApplied(view);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not apply the override.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog title="Override result" onClose={onClose}>
      <p style={{ color: "var(--tk-muted)", marginTop: 0, fontSize: 14 }}>
        {nameOf(match.homeId)} vs {match.awayId ? nameOf(match.awayId) : "—"}
      </p>
      <form onSubmit={onSubmit}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div>
            <div className="tk-label" style={{ marginBottom: 6 }}>{nameOf(match.homeId)}</div>
            <input className="tk-input" type="number" min={0} value={home}
              onChange={(e) => setHome(e.target.value === "" ? 0 : Number(e.target.value))} />
          </div>
          <div>
            <div className="tk-label" style={{ marginBottom: 6 }}>{match.awayId ? nameOf(match.awayId) : "Away"}</div>
            <input className="tk-input" type="number" min={0} value={away}
              onChange={(e) => setAway(e.target.value === "" ? 0 : Number(e.target.value))} />
          </div>
        </div>

        <div style={{ marginTop: 14 }}>
          <div className="tk-label" style={{ marginBottom: 6 }}>Reason (required, audited)</div>
          <input className="tk-input" value={reason} onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. scoreboard error" autoFocus />
          {!validReason && reason.length > 0 && (
            <div style={{ color: "var(--tk-danger)", fontSize: 12, marginTop: 4 }}>A reason is required.</div>
          )}
        </div>

        {error && <div role="alert" style={{ color: "var(--tk-danger)", fontSize: 14, marginTop: 12 }}>{error}</div>}

        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 18 }}>
          <button type="button" className="tk-btn tk-btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="tk-btn tk-btn-primary" disabled={!validScore || !validReason || busy}>
            {busy ? "Applying…" : "Apply override"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
