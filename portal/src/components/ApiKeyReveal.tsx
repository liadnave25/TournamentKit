// Shows a freshly minted API key ONCE, with a clear "copy now — it won't be shown again" warning.
// Reused by first-project creation and (session 12) key rotation, mirroring the same UX.
import { useState } from "react";

export function ApiKeyReveal({ apiKey, onDone }: { apiKey: string; onDone: () => void }) {
  const [copied, setCopied] = useState(false);

  // Copies the key to the clipboard and flips the button label briefly.
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(apiKey);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="tk-card" style={{ padding: 22, maxWidth: 520 }}>
      <div className="tk-label" style={{ color: "var(--tk-primary)" }}>Your API key</div>
      <p style={{ color: "var(--tk-muted)", marginTop: 8, marginBottom: 16, lineHeight: 1.5 }}>
        Copy it now and store it somewhere safe — for security it is{" "}
        <strong style={{ color: "var(--tk-on-surface)" }}>shown only once</strong> and cannot be
        retrieved again. You can always rotate to a new key later.
      </p>

      <div
        className="tk-num"
        style={{
          display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
          background: "var(--tk-surface)", border: "1px solid var(--tk-line)",
          borderRadius: "var(--tk-radius-sm)", padding: "12px 14px",
          wordBreak: "break-all", fontSize: 14,
        }}
      >
        <span>{apiKey}</span>
      </div>

      <div style={{ display: "flex", gap: 10, marginTop: 16 }}>
        <button className="tk-btn tk-btn-primary" onClick={copy}>
          {copied ? "Copied ✓" : "Copy key"}
        </button>
        <button className="tk-btn tk-btn-ghost" onClick={onDone}>
          I've saved it — continue
        </button>
      </div>
    </div>
  );
}
