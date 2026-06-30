// The TournamentKit wordmark + glyph, used on the full-width top bar and on auth screens.
export function Brand({ size = 22 }: { size?: number }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
      <span
        aria-hidden
        style={{
          width: size + 14, height: size + 14, borderRadius: "var(--tk-radius)",
          background: "var(--tk-surface-3)",
          color: "var(--tk-primary)",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
        }}
      >
        <span className="material-symbols-outlined" style={{ fontSize: size }}>sports_esports</span>
      </span>
      <span className="tk-display" style={{ fontSize: size, letterSpacing: "-0.02em" }}>
        Tournament<span style={{ color: "var(--tk-primary)" }}>Kit</span>
      </span>
    </div>
  );
}
