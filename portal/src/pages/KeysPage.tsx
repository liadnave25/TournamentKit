// API key management. Only a hash is stored server-side, so there's nothing to list — the only action
// is rotate, which returns a brand-new key shown ONCE (reusing ApiKeyReveal) and invalidates the old one.
import { useState } from "react";
import { api, ApiError } from "../lib/api";
import { usePid } from "../lib/usePid";
import { ApiKeyReveal } from "../components/ApiKeyReveal";
import { ErrorState } from "../components/StateBlock";

export function KeysPage() {
  const pid = usePid();
  const [newKey, setNewKey] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Rotates the key; on success shows it once via ApiKeyReveal.
  const rotate = async () => {
    setError(null);
    setBusy(true);
    try {
      const res = await api.rotateKey(pid);
      setNewKey(res.apiKey);
      setConfirming(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not rotate the key.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ maxWidth: 620, margin: "0 auto", padding: "32px 22px" }}>
      <h1 className="tk-display" style={{ fontSize: 26, margin: "0 0 8px" }}>API Keys</h1>
      <p style={{ color: "var(--tk-muted)", marginTop: 0, marginBottom: 22, lineHeight: 1.5 }}>
        Your SDK authenticates with this project's API key. For security the server stores only a
        <strong style={{ color: "var(--tk-on-surface)" }}> hash</strong> of the key — it can't be shown again,
        so there's no list here. Rotate to get a new key; the old key stops working immediately.
      </p>

      {error && <div style={{ marginBottom: 16 }}><ErrorState message={error} /></div>}

      {newKey ? (
        <ApiKeyReveal apiKey={newKey} onDone={() => setNewKey(null)} />
      ) : confirming ? (
        <div className="tk-card" style={{ padding: 20, maxWidth: 520 }}>
          <div className="tk-label" style={{ color: "var(--tk-danger)" }}>Rotate API key?</div>
          <p style={{ color: "var(--tk-muted)", marginTop: 8, marginBottom: 16 }}>
            The current key will stop working <strong style={{ color: "var(--tk-on-surface)" }}>immediately</strong>.
            Any app still using it must be updated with the new key.
          </p>
          <div style={{ display: "flex", gap: 10 }}>
            <button className="tk-btn tk-btn-primary" onClick={rotate} disabled={busy}>
              {busy ? "Rotating…" : "Rotate key now"}
            </button>
            <button className="tk-btn tk-btn-ghost" onClick={() => setConfirming(false)} disabled={busy}>Cancel</button>
          </div>
        </div>
      ) : (
        <button className="tk-btn tk-btn-primary" onClick={() => setConfirming(true)}>Rotate API key</button>
      )}
    </div>
  );
}
