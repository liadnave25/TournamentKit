// The TournamentKit wordmark + floodlight glyph, used in the top bar and on auth screens.
export function Brand({ size = 20 }: { size?: number }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
      <span
        aria-hidden
        style={{
          width: size, height: size, borderRadius: 6,
          background: "linear-gradient(135deg, var(--tk-primary), #ff8a00)",
          boxShadow: "0 0 16px rgba(255,176,32,0.5)",
          display: "inline-block",
        }}
      />
      <span className="tk-display" style={{ fontSize: size * 0.92, letterSpacing: "-0.02em" }}>
        Tournament<span style={{ color: "var(--tk-primary)" }}>Kit</span>
      </span>
    </div>
  );
}
