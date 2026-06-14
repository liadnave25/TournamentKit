// Pure bracket layout math — the React port of the SDK's Compose BracketLayout.kt.
// Same model: round → x (columns), round-1 slot → y (rows), each later match vertically CENTERED
// between the two matches that feed it (its children via nextMatchId), with elbow connectors.
import type { Match } from "../../lib/types";

export interface BracketPos {
  matchId: string;
  x: number;
  y: number;
}

export interface BracketConnector {
  fromId: string;
  toId: string;
}

export interface BracketLayout {
  positions: Record<string, BracketPos>;
  connectors: BracketConnector[];
  width: number;
  height: number;
}

// Card + gap sizing (px). Exported so the BracketView draws cards/connectors with the same numbers.
export interface BracketMetrics {
  cardWidth: number;
  cardHeight: number;
  hGap: number; // horizontal gap between rounds
  vGap: number; // vertical gap between round-1 cards
}

export const DEFAULT_METRICS: BracketMetrics = {
  cardWidth: 200,
  cardHeight: 92,
  hGap: 56,
  vGap: 26,
};

// Computes the left→right bracket layout from the engine's match list. Pure; no DOM.
export function computeBracketLayout(matches: Match[], metrics: BracketMetrics = DEFAULT_METRICS): BracketLayout {
  if (matches.length === 0) return { positions: {}, connectors: [], width: 0, height: 0 };

  const byId: Record<string, Match> = {};
  for (const m of matches) byId[m.id] = m;

  const rounds = Array.from(new Set(matches.map((m) => m.round))).sort((a, b) => a - b);
  const firstRound = rounds[0];

  // Column x for a round (rounds are contiguous in a single-elimination bracket).
  const xForRound = (round: number) => (round - firstRound) * (metrics.cardWidth + metrics.hGap);

  const positions: Record<string, BracketPos> = {};

  // Round 1 (leaves): stack by slot order — the vertical anchor for the whole tree.
  matches
    .filter((m) => m.round === firstRound)
    .sort((a, b) => a.slot - b.slot)
    .forEach((m, index) => {
      positions[m.id] = { matchId: m.id, x: xForRound(m.round), y: index * (metrics.cardHeight + metrics.vGap) };
    });

  // Later rounds: a parent's y is the mean of its already-placed children's y (parent-centering).
  for (const round of rounds.slice(1)) {
    matches
      .filter((m) => m.round === round)
      .sort((a, b) => a.slot - b.slot)
      .forEach((m) => {
        const childYs = matches
          .filter((c) => c.nextMatchId === m.id)
          .map((c) => positions[c.id]?.y)
          .filter((y): y is number => y !== undefined);
        const y =
          childYs.length > 0
            ? childYs.reduce((a, b) => a + b, 0) / childYs.length
            : m.slot * (metrics.cardHeight + metrics.vGap);
        positions[m.id] = { matchId: m.id, x: xForRound(round), y };
      });
  }

  // One connector per match that advances (winner feeds nextMatchId).
  const connectors: BracketConnector[] = matches
    .filter((m) => m.nextMatchId != null && byId[m.nextMatchId])
    .map((m) => ({ fromId: m.id, toId: m.nextMatchId as string }));

  const width = rounds.length * metrics.cardWidth + (rounds.length - 1) * metrics.hGap;
  const maxY = Math.max(0, ...Object.values(positions).map((p) => p.y));
  const height = maxY + metrics.cardHeight;
  return { positions, connectors, width, height };
}
