// Confirm/cancel dialog for hard-deleting a tournament, calling DELETE and surfacing the server's TKError inline.
import { useState } from "react";
import { api, ApiError } from "../lib/api";
import { Dialog } from "./Dialog";

export function DeleteTournamentDialog({
  pid, tid, name, onClose, onDeleted,
}: {
  pid: string;
  tid: string;
  name: string;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Calls DELETE; on success hands control back to the caller (which navigates + refreshes).
  const confirm = async () => {
    setError(null);
    setBusy(true);
    try {
      await api.deleteTournament(pid, tid);
      onDeleted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the tournament.");
      setBusy(false);
    }
  };

  return (
    <Dialog title="Delete tournament?" onClose={onClose}>
      <p style={{ color: "var(--tk-muted)", marginTop: 0 }}>
        Are you sure? <strong style={{ color: "var(--tk-on-surface)" }}>{name}</strong> and all of its
        participants, standings, and matches will be permanently deleted. This can't be undone.
      </p>
      {error && <div role="alert" style={{ color: "var(--tk-danger)", fontSize: 14, marginBottom: 12 }}>{error}</div>}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button className="tk-btn tk-btn-ghost" onClick={onClose} disabled={busy}>Cancel</button>
        <button
          className="tk-btn"
          style={{ background: "var(--tk-danger)", color: "#fff", borderColor: "transparent" }}
          onClick={confirm}
          disabled={busy}
        >
          {busy ? "Deleting…" : "Delete"}
        </button>
      </div>
    </Dialog>
  );
}
