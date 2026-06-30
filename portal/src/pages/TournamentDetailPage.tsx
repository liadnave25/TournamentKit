// Full visual tournament detail plus admin actions (bracket/standings, participants, freeze, override, audit) over GET /tournaments/{tid}.
import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, ApiError } from "../lib/api";
import { usePid } from "../lib/usePid";
import { useAsync } from "../lib/useAsync";
import { Loading, ErrorState, Empty } from "../components/StateBlock";
import { StatusChip } from "../components/StatusChip";
import { OverrideDialog } from "../components/OverrideDialog";
import { DeleteTournamentDialog } from "../components/DeleteTournamentDialog";
import { AuditLog } from "../components/AuditLog";
import { SdkLogs } from "../components/SdkLogs";
import { BracketView } from "../components/visual/BracketView";
import { LeagueTableView } from "../components/visual/LeagueTableView";
import { MatchCard } from "../components/visual/MatchCard";
import type { AuditEntry, Match, SdkLogEntry, TournamentView } from "../lib/types";

export function TournamentDetailPage() {
  const pid = usePid();
  const navigate = useNavigate();
  const { tid } = useParams<{ tid: string }>();
  const tournamentId = tid as string;

  const view = useAsync<TournamentView>(() => api.getTournament(pid, tournamentId), [pid, tournamentId]);
  const audit = useAsync<AuditEntry[]>(() => api.getAudit(pid, tournamentId), [pid, tournamentId]);
  const logs = useAsync<SdkLogEntry[]>(() => api.getSdkLogs(pid, tournamentId), [pid, tournamentId]);

  const [tab, setTab] = useState<"overview" | "audit" | "logs">("overview");
  const [overriding, setOverriding] = useState<Match | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);

  // Refreshes the tournament view, audit log, and SDK logs after any admin action.
  const refreshAll = () => { view.reload(); audit.reload(); logs.reload(); };

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

  // Real progress: confirmed matches over total (TALLY has no matches, so it's hidden there).
  const confirmedCount = matches.filter((m) => m.status === "CONFIRMED").length;
  const progressPct = matches.length > 0 ? Math.round((confirmedCount / matches.length) * 100) : 0;

  return (
    <Pad>
      <Link to={`/projects/${pid}/tournaments`} className="tk-crumbs" style={{ fontSize: 13, textDecoration: "none" }}>
        <span className="material-symbols-outlined" style={{ fontSize: 18 }} aria-hidden>chevron_left</span>
        All tournaments
      </Link>

      {/* Header + admin actions. */}
      <div style={{ display: "flex", alignItems: "flex-start", gap: 14, margin: "12px 0 4px" }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <h1 className="tk-display" style={{ fontSize: 24, margin: 0 }}>{tournament.name}</h1>
            <StatusChip status={tournament.status} />
          </div>
          <div className="tk-num" style={{ color: "var(--tk-muted)", fontSize: 13, marginTop: 6 }}>ID: {tournament.id}</div>
        </div>
        {tournament.status === "ACTIVE" && (
          <button className="tk-btn" onClick={() => toggleFreeze(true)} disabled={actionBusy}>
            <span className="material-symbols-outlined" style={{ fontSize: 18 }} aria-hidden>ac_unit</span>
            Freeze
          </button>
        )}
        {tournament.status === "FROZEN" && (
          <button className="tk-btn tk-btn-primary" onClick={() => toggleFreeze(false)} disabled={actionBusy}>
            <span className="material-symbols-outlined" style={{ fontSize: 18 }} aria-hidden>play_arrow</span>
            Unfreeze
          </button>
        )}
        <button
          className="tk-btn"
          style={{ color: "var(--tk-danger)", borderColor: "rgba(186,26,26,0.3)", background: "var(--tk-surface-2)" }}
          onClick={() => setDeleting(true)}
          disabled={actionBusy}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 18 }} aria-hidden>delete</span>
          Delete
        </button>
      </div>
      <div style={{ color: "var(--tk-muted)", fontSize: 13, marginBottom: 8 }}>
        {isTally ? "TALLY · points leaderboard" : <>{type} · code <span className="tk-num">{tournament.joinCode}</span></>}
        {" · "}{tournament.participants.length} participants
      </div>
      {actionError && <div style={{ marginBottom: 14 }}><ErrorState message={actionError} /></div>}

      {/* Tabs: Overview (visual + participants), Audit log (admin actions), Logs (SDK calls). */}
      <div style={{ display: "flex", gap: 28, marginTop: 18, borderBottom: "1px solid var(--tk-line)" }}>
        {(["overview", "audit", "logs"] as const).map((t) => (
          <button
            key={t}
            className={`tk-tab${tab === t ? " active" : ""}`}
            onClick={() => setTab(t)}
          >
            {t === "overview" ? "Overview" : t === "audit" ? "Audit Log" : "SDK Logs"}
          </button>
        ))}
      </div>

      {tab === "overview" && (
      <>
      {/* Tournament progress (real: confirmed / total matches), skipped for TALLY which has no matches. */}
      {!isTally && matches.length > 0 && (
        <div className="tk-card" style={{ padding: 20, marginTop: 24 }}>
          <div className="tk-label" style={{ marginBottom: 10 }}>Tournament Progress</div>
          <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", marginBottom: 10 }}>
            <span className="tk-num" style={{ fontSize: 30 }}>{progressPct}%</span>
            <span style={{ color: "var(--tk-muted)", fontSize: 13 }}>{confirmedCount}/{matches.length} matches confirmed</span>
          </div>
          <div className="tk-track"><div className="tk-fill" style={{ width: `${progressPct}%` }} /></div>
        </div>
      )}

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
      </>
      )}

      {/* Audit log: admin actions (freeze/unfreeze/override). */}
      {tab === "audit" && (
        <Section title="Audit log">
          {audit.loading && <Loading label="Loading audit…" />}
          {audit.error && <ErrorState message={audit.error.message} onRetry={audit.reload} />}
          {audit.data && <AuditLog entries={audit.data} />}
        </Section>
      )}

      {/* SDK logs: every /v1 SDK call against this tournament, success or failure. */}
      {tab === "logs" && (
        <Section title="SDK logs">
          {logs.loading && <Loading label="Loading logs…" />}
          {logs.error && <ErrorState message={logs.error.message} onRetry={logs.reload} />}
          {logs.data && <SdkLogs entries={logs.data} />}
        </Section>
      )}

      {overriding && (
        <OverrideDialog
          pid={pid} tid={tournamentId} match={overriding} nameOf={nameOf}
          onClose={() => setOverriding(null)}
          onApplied={() => { setOverriding(null); refreshAll(); }}
        />
      )}

      {deleting && (
        <DeleteTournamentDialog
          pid={pid} tid={tournamentId} name={tournament.name}
          onClose={() => setDeleting(false)}
          // Deleted: leave the (now-gone) detail page and land on a freshly-loaded list.
          onDeleted={() => navigate(`/projects/${pid}/tournaments`, { replace: true })}
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
  return <div style={{ maxWidth: 1200, padding: 24 }}>{children}</div>;
}

// Heuristic: a match with no nextMatchId that still has a round > 1 looks like a knockout final.
function isFinalLike(m: Match, all: Match[]): boolean {
  const maxRound = Math.max(...all.map((x) => x.round));
  return m.round === maxRound && all.some((x) => x.nextMatchId === m.id);
}
