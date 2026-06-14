// A left→right single-elimination bracket with elbow connectors, horizontally scrollable.
// Uses the pure layout math (computeBracketLayout) and the same MatchCard as lists.
// React port of the SDK's Compose BracketView, same Floodlight look.
import type { Match } from "../../lib/types";
import { computeBracketLayout, DEFAULT_METRICS } from "./bracketLayout";
import { MatchCard } from "./MatchCard";
import { Empty } from "../StateBlock";

export function BracketView({
  matches,
  nameOf,
  onOverride,
}: {
  matches: Match[];
  nameOf: (id: string) => string;
  onOverride?: (match: Match) => void;
}) {
  if (matches.length === 0) return <Empty label="No bracket yet" />;

  const m = DEFAULT_METRICS;
  const layout = computeBracketLayout(matches, m);
  const byId: Record<string, Match> = {};
  for (const match of matches) byId[match.id] = match;

  return (
    // Horizontal scroll for large brackets; the inner canvas is fixed-size.
    <div style={{ overflowX: "auto", overflowY: "hidden", padding: "4px 2px 16px" }}>
      <div style={{ position: "relative", width: layout.width, height: layout.height, minWidth: layout.width }}>
        {/* Connector lines drawn underneath the cards. */}
        <svg
          width={layout.width}
          height={layout.height}
          style={{ position: "absolute", inset: 0, pointerEvents: "none" }}
          aria-hidden
        >
          {layout.connectors.map((c) => {
            const from = layout.positions[c.fromId];
            const to = layout.positions[c.toId];
            if (!from || !to) return null;
            // Child right-center → parent left-center, as a 3-segment elbow.
            const startX = from.x + m.cardWidth;
            const startY = from.y + m.cardHeight / 2;
            const endX = to.x;
            const endY = to.y + m.cardHeight / 2;
            const midX = (startX + endX) / 2;
            return (
              <polyline
                key={`${c.fromId}->${c.toId}`}
                points={`${startX},${startY} ${midX},${startY} ${midX},${endY} ${endX},${endY}`}
                fill="none"
                stroke="var(--tk-line)"
                strokeWidth={2}
              />
            );
          })}
        </svg>

        {/* The match cards at their computed offsets. */}
        {Object.values(layout.positions).map((pos) => {
          const match = byId[pos.matchId];
          if (!match) return null;
          return (
            <div
              key={pos.matchId}
              style={{ position: "absolute", left: pos.x, top: pos.y, width: m.cardWidth }}
            >
              <MatchCard match={match} nameOf={nameOf} onOverride={onOverride} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
