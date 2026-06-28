// TypeScript mirrors of the server's API shapes (see docs/portal-api.md). Keep in sync with :shared.

// The typed error every endpoint returns on failure: { code, message }.
export type TKErrorCode =
  | "TK_NOT_AUTHENTICATED"
  | "TK_TOURNAMENT_NOT_FOUND"
  | "TK_TOURNAMENT_LOCKED"
  | "TK_TOURNAMENT_FROZEN"
  | "TK_FORBIDDEN"
  | "TK_INVALID_SCORE"
  | "TK_ALREADY_JOINED"
  | "TK_TOURNAMENT_FULL"
  | "TK_MATCH_ALREADY_REPORTED"
  | "TK_NOT_PARTICIPANT"
  | "TK_NOT_SUPPORTED_FOR_TYPE"
  | "TK_UNKNOWN";

export interface TKErrorBody {
  code: TKErrorCode;
  message: string;
}

// A project the developer owns (GET /portal/projects).
export interface ProjectSummary {
  id: string;
  name: string;
  createdAt: number;
}

// Result of creating a project (POST /portal/projects) — apiKey shown once.
export interface CreateProjectResponse {
  id: string;
  name: string;
  apiKey: string;
}

// Tournament template (GET/POST /templates), where TALLY is an open-ended points leaderboard with no matches.
export type TemplateType = "KNOCKOUT" | "LEAGUE" | "GROUPS_KNOCKOUT" | "TALLY";
export interface Template {
  id: string;
  type: TemplateType;
  scoring: { win: number; draw: number; loss: number };
  maxParticipants: number;
}

// Lightweight tournament row (GET /tournaments).
export type TournamentStatus = "REGISTRATION" | "ACTIVE" | "FROZEN" | "FINISHED";
export interface TournamentSummary {
  id: string;
  name: string;
  status: TournamentStatus;
  participantCount: number;
  createdAt: number;
}

// A player in a tournament (Tournament.participants).
export interface Participant {
  userId: string;
  displayName: string;
  avatarUrl?: string | null;
  seed?: number | null;
}

// A match score.
export interface TKScore {
  home: number;
  away: number;
}

// Where a match stands; under the single-writer model a reported result is final immediately (no REPORTED step).
export type MatchStatus = "PENDING" | "CONFIRMED";

// One game: two sides, an optional score, and a pointer to where the winner advances.
// awayId === null is a BYE; an empty-string id ("") is a not-yet-known (TBD) slot.
export interface Match {
  id: string;
  round: number;
  slot: number;
  homeId: string;
  awayId?: string | null;
  score?: TKScore | null;
  status: MatchStatus;
  nextMatchId?: string | null;
}

// Full tournament document (within a tournament detail view).
export interface Tournament {
  id: string;
  projectId: string;
  templateId: string;
  name: string;
  joinCode: string;
  status: TournamentStatus;
  participants: Participant[];
  rules: Template;
  createdAt: number;
  startedAt?: number | null;
}

// Standings row (within a tournament view).
export interface Standing {
  userId: string;
  played: number;
  won: number;
  drawn: number;
  lost: number;
  pointsFor: number;
  pointsAgainst: number;
  points: number;
}

// Full tournament detail (GET /tournaments/{id}): the tournament plus its matches and standings.
export interface TournamentView {
  tournament: Tournament;
  matches: Match[];
  standings: Standing[];
}

// One audit-log entry (GET /tournaments/{id}/audit) where only action/adminUid/timestamp are always present.
export interface AuditEntry {
  action: string;
  adminUid?: string;
  timestamp?: number;
  matchId?: string;
  reason?: string;
  oldScore?: TKScore;
  newScore?: TKScore;
}

// One SDK-call log entry (GET /tournaments/{id}/logs): action/outcome/timestamp always present.
// The request payload + error detail are captured on FAILURE only, so the developer can inspect the rejected input.
export interface SdkLogEntry {
  action: string;
  outcome: "SUCCESS" | "FAILURE";
  timestamp?: number;
  userId?: string;
  matchId?: string;
  errorCode?: string;
  errorMessage?: string;
  payload?: Record<string, unknown>;
}

// Result of rotating the API key (POST /keys/rotate) — apiKey shown once.
export interface RotateKeyResponse {
  apiKey: string;
}

// Dashboard numbers (GET /analytics).
export interface Analytics {
  tournamentsTotal: number;
  tournamentsByStatus: Record<string, number>;
  participantsTotal: number;
  matchesConfirmed: number;
  lastTournamentCreatedAt: number | null;
}
