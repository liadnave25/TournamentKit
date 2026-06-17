// Full visual tournament detail + admin actions. Renders a bracket (knockout / groups→knockout) and/or
// standings (league / groups), the participants, freeze/unfreeze, per-match result override, and the
// audit log. All data comes from GET /tournaments/{tid}; actions refresh it.
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, ApiError } from "../lib/api";
import { usePid } from "../lib/usePid";
import { useAsync } from "../lib/useAsync";
import { Loading, ErrorState, Empty } from "../components/StateBlock";
import { StatusChip } from "../components/StatusChip";
import { OverrideDialog } from "../components/OverrideDialog";
import { AuditLog } from "../components/AuditLog";
import { BracketView } from "../components/visual/BracketView";
import { LeagueTableView } from "../components/visual/LeagueTableView";
import { MatchCard } from "../components/visual/MatchCard";
import type { AuditEntry, Match, TournamentView } from "../lib/types";

export function TournamentDetailPage() {
  const pid = usePid();
  const { tid } = useParams<{ tid: string }>();
  const tournamentId = tid as string;

  const view = useAsync<TournamentView>(() => api.getTournament(pid, tournamentId), [pid, tournamentId]);
  const audit = useAsync<AuditEntry[]>(() => api.getAudit(pid, tournamentId), [pid, tournamentId]);

  const [overriding, setOverriding] = useState<Match | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);

  // Refreshes both the tournament view and the audit log after any admin action.
  const refreshAll = () => { view.reload(); audit.reload(); };

  // Freeze/unfreeze share this handler; the server's TKError (e.g. wrong state) is surfaced.
  const toggleFreeze = async (freeze: boolean) => {
    setActionError(null);
    setActionBusy(true);
    try {
      if (freeze) await api.freezeTournament(pid, tournamentId);
      else await api.unfreezeTournament(pid, tournamentId);
      refreshAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Action failed.");
    } finally {
      setActionBusy(false);
    }
  };

  // userId → display name from the tournament's participants (falls back to the raw id).
  const nameOf = useMemo(() => {
    const map: Record<string, string> = {};
    for (const p of view.data?.tournament.participants ?? []) map[p.userId] = p.displayName;
    return (id: string) => map[id] ?? id;
  }, [view.data]);

  if (view.loading) return <Pad><Loading label="Loading tournament…" /></Pad>;
  if (view.error) return <Pad><ErrorState message={view.error.message} onRetry={view.reload} /></Pad>;
  if (!view.data) return null;

  const { tournament, matches, standings } = view.data;
  const type = tournament.rules.type;
  // TALLY is a points leaderboard with no matches: no bracket, no match list — just standings.
  const isTally = type === "TALLY";
  const showBracket = type === "KNOCKOUT" || type === "GROUPS_KNOCKOUT";
  const showTable = type === "LEAGUE" || type === "GROUPS_KNOCKOUT" || isTally;
  // Knockout matches advance someone; group matches don't — split them for groups→knockout.
  const knockoutMatches = matches.filter((m) => m.nextMatchId != null || isFinalLike(m, matches));

  return (
    <Pad>
      <Link to={`/projects/${pid}/tournaments`} style={{ fontSize: 13 }}>← All tournaments</Link>

      {/* Header + admin actions. */}
      <div style={{ display: "flex", alignItems: "center", gap: 14, margin: "10px 0 4px" }}>
        <h1 className="tk-display" style={{ fontSize: 26, margin: 0 }}>{tournament.name}</h1>
        <StatusChip status={tournament.status} />
        <div style={{ flex: 1 }} />
        {tournament.status === "ACTIVE" && (
          <button className="tk-btn" onClick={() => toggleFreeze(true)} disabled={actionBusy}>Freeze</button>
        )}
        {tournament.status === "FROZEN" && (
          <button className="tk-btn tk-btn-primary" onClick={() => toggleFreeze(false)} disabled={actionBusy}>Unfreeze</button>
        )}
      </div>
      <div style={{ color: "var(--tk-muted)", fontSize: 13, marginBottom: 8 }}>
        {isTally ? "TALLY · points leaderboard" : <>{type} · code <span className="tk-num">{tournament.joinCode}</span></>}
        {" · "}{tournament.participants.length} participants
      </div>
      {actionError && <div style={{ marginBottom: 14 }}><ErrorState message={actionError} /></div>}

      {/* Visual: bracket and/or standings. */}
      {showBracket && (
        <Section title="Bracket">
          <BracketView
            matches={type === "GROUPS_KNOCKOUT" ? knockoutMatches : matches}
            nameOf={nameOf}
            onOverride={(m) => setOverriding(m)}
          />
        </Section>
      )}

      {showTable && (
        <Section title={isTally ? "Leaderboard" : "Standings"}>
          <LeagueTableView standings={standings} nameOf={nameOf} />
        </Section>
      )}

      {/* For league/groups the matches don't form a bracket; list them so overrides are reachable.
          TALLY has no matches at all, so it's skipped here. */}
      {type !== "KNOCKOUT" && !isTally && (
        <Section title="Matches">
          <MatchList matches={type === "GROUPS_KNOCKOUT" ? matches.filter((m) => m.nextMatchId == null) : matches}
            nameOf={nameOf} onOverride={(m) => setOverriding(m)} />
        </Section>
      )}

      {/* Participants. */}
      <Section title="Participants">
        {tournament.participants.length === 0 ? (
          <Empty label="No participants." />
        ) : (
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            {tournament.participants.map((p) => (
              <span key={p.userId} className="tk-pill" style={{ color: "var(--tk-on-surface)" }}>
                {p.displayName}
              </span>
            ))}
          </div>
        )}
      </Section>

      {/* Audit log. */}
      <Section title="Audit log">
        {audit.loading && <Loading label="Loading audit…" />}
        {audit.error && <ErrorState message={audit.error.message} onRetry={audit.reload} />}
        {audit.data && <AuditLog entries={audit.data} />}
      </Section>

      {overriding && (
        <OverrideDialog
          pid={pid} tid={tournamentId} match={overriding} nameOf={nameOf}
          onClose={() => setOverriding(null)}
          onApplied={() => { setOverriding(null); refreshAll(); }}
        />
      )}
    </Pad>
  );
}

// A simple list of matches (for league/groups) with override actions.
function MatchList({
  matches, nameOf, onOverride,
}: { matches: Match[]; nameOf: (id: string) => string; onOverride: (m: Match) => void }) {
  if (matches.length === 0) return <Empty label="No matches yet." />;
  return (
    <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))" }}>
      {matches
        .slice()
        .sort((a, b) => a.round - b.round || a.slot - b.slot)
        .map((m) => <MatchCard key={m.id} match={m} nameOf={nameOf} onOverride={onOverride} />)}
    </div>
  );
}

// A titled section wrapper.
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ marginTop: 28 }}>
      <div className="tk-label" style={{ marginBottom: 12 }}>{title}</div>
      {children}
    </section>
  );
}

// Page padding wrapper.
function Pad({ children }: { children: React.ReactNode }) {
  return <div style={{ maxWidth: 1080, margin: "0 auto", padding: "24px 22px" }}>{children}</div>;
}

// Heuristic: a match with no nextMatchId that still has a round > 1 looks like a knockout final.
function isFinalLike(m: Match, all: Match[]): boolean {
  const maxRound = Math.max(...all.map((x) => x.round));
  return m.round === maxRound && all.some((x) => x.nextMatchId === m.id);
}
