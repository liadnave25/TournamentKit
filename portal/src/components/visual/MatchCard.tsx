// A single match card: two sides, the score if present, a status chip, BYE/TBD states, winner emphasis.
// Presentation only — pass nameOf to resolve userIds to display names, and an optional onOverride action.
// React port of the SDK's Compose MatchCard, same Floodlight look.
import type { Match } from "../../lib/types";

const TBD_ID = ""; // mirrors the engine's TBD slot id

// Renders one match, sized by the caller (BracketView) or flowing naturally in lists.
export function MatchCard({
  match,
  nameOf,
  onOverride,
  style,
}: {
  match: Match;
  nameOf: (id: string) => string;
  onOverride?: (match: Match) => void;
  style?: React.CSSProperties;
}) {
  const isBye = match.awayId == null;
  const winner = winningSide(match);

  return (
    <div
      style={{
        background: "var(--tk-surface-2)",
        border: "1px solid var(--tk-line)",
        borderRadius: 12,
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        ...style,
      }}
    >
      <Side
        name={slotLabel(match.homeId, nameOf)}
        score={match.score?.home}
        isWinner={match.status === "CONFIRMED" && winner === "HOME"}
        placeholder={match.homeId === TBD_ID}
      />
      <div style={{ height: 1, background: "var(--tk-line)", margin: "0 10px" }} />
      {isBye ? (
        <div style={{ padding: "9px 12px", color: "var(--tk-muted)", fontSize: 13 }}>
          BYE — auto-advanced
        </div>
      ) : (
        <Side
          name={slotLabel(match.awayId as string, nameOf)}
          score={match.score?.away}
          isWinner={match.status === "CONFIRMED" && winner === "AWAY"}
          placeholder={match.awayId === TBD_ID}
        />
      )}

      <Footer match={match} isBye={isBye} onOverride={onOverride} />
    </div>
  );
}

// One participant row: gold winner bar + bold name, score on the right (mono).
function Side({
  name,
  score,
  isWinner,
  placeholder,
}: {
  name: string;
  score?: number;
  isWinner: boolean;
  placeholder: boolean;
}) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "9px 12px" }}>
      {isWinner && (
        <span style={{ width: 3, height: 16, borderRadius: 2, background: "var(--tk-primary)" }} />
      )}
      <span
        style={{
          flex: 1,
          fontFamily: "var(--tk-font-body)",
          fontWeight: isWinner ? 800 : 500,
          color: placeholder ? "var(--tk-muted)" : "var(--tk-on-surface)",
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {name}
      </span>
      <span
        className="tk-num"
        style={{ fontSize: 16, color: isWinner ? "var(--tk-primary)" : "var(--tk-muted)" }}
      >
        {score ?? "–"}
      </span>
    </div>
  );
}

// Status chip footer (+ an optional override action for admins).
function Footer({
  match,
  isBye,
  onOverride,
}: {
  match: Match;
  isBye: boolean;
  onOverride?: (match: Match) => void;
}) {
  const hasTbd = match.homeId === TBD_ID || (!isBye && match.awayId === TBD_ID);
  const { text, color } = statusLabel(match, hasTbd);
  // Override only makes sense once both real players are known (no TBD, not a BYE row).
  const canOverride = onOverride && !hasTbd && !isBye;

  return (
    <div
      style={{
        display: "flex", alignItems: "center", gap: 8,
        padding: "6px 12px", borderTop: "1px solid var(--tk-line)",
      }}
    >
      <span className="tk-pill" style={{ color, border: "none", padding: 0 }}>
        <span className="tk-dot" style={{ background: color }} /> {text}
      </span>
      <div style={{ flex: 1 }} />
      {canOverride && (
        <button
          className="tk-btn tk-btn-ghost"
          style={{ padding: "4px 9px", fontSize: 11 }}
          onClick={() => onOverride!(match)}
        >
          Override
        </button>
      )}
    </div>
  );
}

// Maps the match state to a chip label + Floodlight color.
function statusLabel(match: Match, hasTbd: boolean): { text: string; color: string } {
  if (hasTbd) return { text: "AWAITING OPPONENT", color: "var(--tk-muted)" };
  if (match.status === "CONFIRMED") return { text: "CONFIRMED", color: "var(--tk-winner)" };
  return { text: "PENDING", color: "var(--tk-muted)" };
}

// A slot's label: a real player's name, or "TBD" for an empty future slot.
function slotLabel(id: string, nameOf: (id: string) => string): string {
  return id === TBD_ID ? "TBD" : nameOf(id);
}

// Which side won, from the score (meaningful once CONFIRMED).
function winningSide(match: Match): "HOME" | "AWAY" | "NONE" {
  if (!match.score) return "NONE";
  if (match.score.home > match.score.away) return "HOME";
  if (match.score.away > match.score.home) return "AWAY";
  return "NONE";
}
