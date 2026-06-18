// A league standings table (#, TEAM, P, W, D, L, GD, Pts) over the already-sorted list, a React port of the Compose view.
import type { Standing } from "../../lib/types";
import { Empty } from "../StateBlock";

export function LeagueTableView({
  standings,
  nameOf,
}: {
  standings: Standing[];
  nameOf: (id: string) => string;
}) {
  if (standings.length === 0) return <Empty label="No standings yet" />;

  return (
    <div className="tk-card" style={{ overflow: "hidden" }}>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr style={{ background: "var(--tk-surface)" }}>
            <Th style={{ width: 36, textAlign: "left" }}>#</Th>
            <Th style={{ textAlign: "left" }}>Team</Th>
            <Th>P</Th>
            <Th>W</Th>
            <Th>D</Th>
            <Th>L</Th>
            <Th>GD</Th>
            <Th>Pts</Th>
          </tr>
        </thead>
        <tbody>
          {standings.map((s, i) => {
            const gd = s.pointsFor - s.pointsAgainst;
            const leader = i === 0;
            return (
              <tr key={s.userId} style={{ borderTop: "1px solid var(--tk-line)" }}>
                <Td style={{ textAlign: "left" }}>
                  {leader ? (
                    <span
                      className="tk-label"
                      style={{
                        background: "var(--tk-primary)", color: "var(--tk-on-primary)",
                        padding: "2px 7px", borderRadius: 6,
                      }}
                    >
                      {i + 1}
                    </span>
                  ) : (
                    <span style={{ color: "var(--tk-muted)" }}>{i + 1}</span>
                  )}
                </Td>
                <Td style={{ textAlign: "left", fontWeight: leader ? 800 : 500, fontFamily: "var(--tk-font-body)" }}>
                  {nameOf(s.userId)}
                </Td>
                <Num>{s.played}</Num>
                <Num>{s.won}</Num>
                <Num>{s.drawn}</Num>
                <Num>{s.lost}</Num>
                <Num color={gd > 0 ? "var(--tk-winner)" : "var(--tk-muted)"}>
                  {gd > 0 ? `+${gd}` : gd}
                </Num>
                <Num emphasize={leader}>{s.points}</Num>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// A header cell using the tracked uppercase label style.
function Th({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <th
      className="tk-label"
      style={{ padding: "11px 12px", textAlign: "center", ...style }}
    >
      {children}
    </th>
  );
}

// A plain body cell.
function Td({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return <td style={{ padding: "11px 12px", textAlign: "center", ...style }}>{children}</td>;
}

// A numeric body cell with mono tabular numerals.
function Num({
  children,
  color,
  emphasize,
}: {
  children: React.ReactNode;
  color?: string;
  emphasize?: boolean;
}) {
  return (
    <td
      className="tk-num"
      style={{
        padding: "11px 12px", textAlign: "center", fontSize: 15,
        color: emphasize ? "var(--tk-primary)" : color ?? "var(--tk-on-surface)",
        fontWeight: emphasize ? 800 : 600,
      }}
    >
      {children}
    </td>
  );
}
