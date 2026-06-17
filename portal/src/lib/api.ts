// Typed fetch client for the /portal API. Attaches the Firebase ID token as a Bearer header on every
// request and turns server TKError JSON bodies into a typed ApiError. One place for all API calls.
import { currentIdToken } from "./auth";
import type {
  Analytics,
  AuditEntry,
  CreateProjectResponse,
  ProjectSummary,
  RotateKeyResponse,
  Template,
  TournamentSummary,
  TournamentView,
  TKErrorBody,
  TKErrorCode,
  TKScore,
} from "./types";

// The fields a template create/edit form submits (id is assigned by the server on create).
export interface TemplateInput {
  type: Template["type"];
  scoring: { win: number; draw: number; loss: number };
  maxParticipants: number;
}

// The server to talk to: VITE_API_BASE_URL, or the deployed Cloud Run server by default.
const BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ||
  "https://tournamentkit-server-520238889661.europe-west1.run.app"
).replace(/\/$/, "");

// A typed error carrying the server's TKError code + message (or a transport/network failure).
export class ApiError extends Error {
  code: TKErrorCode;
  status: number;
  constructor(code: TKErrorCode, message: string, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

// Core request helper: adds JSON + Bearer headers, parses the body, and throws ApiError on failure.
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = await currentIdToken();
  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // No connection / CORS / DNS — surface as a clear network error.
    throw new ApiError("TK_UNKNOWN", "Network error — is the server reachable?", 0);
  }

  const text = await res.text();
  const parsed = text ? safeJson(text) : undefined;

  if (!res.ok) {
    const err = parsed as TKErrorBody | undefined;
    throw new ApiError(err?.code ?? "TK_UNKNOWN", err?.message ?? `HTTP ${res.status}`, res.status);
  }
  return parsed as T;
}

// Parses JSON without throwing (some error bodies may be empty or non-JSON).
function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

// Encodes a path segment so ids with odd characters are URL-safe.
const seg = (s: string) => encodeURIComponent(s);

// The portal API surface. One typed method per /portal endpoint (see docs/portal-api.md).
export const api = {
  // ---- projects ----

  // List the projects the signed-in developer owns.
  listProjects: () => request<ProjectSummary[]>("GET", "/portal/projects"),

  // Create a new project; the response includes the first API key (shown once).
  createProject: (name: string) =>
    request<CreateProjectResponse>("POST", "/portal/projects", { name }),

  // ---- analytics ----

  // Dashboard numbers for one project.
  analytics: (pid: string) =>
    request<Analytics>("GET", `/portal/projects/${seg(pid)}/analytics`),

  // ---- templates ----

  // List the project's templates.
  listTemplates: (pid: string) =>
    request<Template[]>("GET", `/portal/projects/${seg(pid)}/templates`),

  // Create a template (server generates the id).
  createTemplate: (pid: string, body: TemplateInput) =>
    request<Template>("POST", `/portal/projects/${seg(pid)}/templates`, body),

  // Update an existing template by id.
  updateTemplate: (pid: string, tid: string, body: TemplateInput) =>
    request<Template>("PUT", `/portal/projects/${seg(pid)}/templates/${seg(tid)}`, body),

  // Delete a template (409 if a non-finished tournament still references it).
  deleteTemplate: (pid: string, tid: string) =>
    request<{ deleted: string }>("DELETE", `/portal/projects/${seg(pid)}/templates/${seg(tid)}`),

  // ---- tournaments ----

  // List tournaments (newest first); optional status filter.
  listTournaments: (pid: string, status?: string) =>
    request<TournamentSummary[]>(
      "GET",
      `/portal/projects/${seg(pid)}/tournaments${status ? `?status=${seg(status)}` : ""}`
    ),

  // Full tournament detail: tournament + matches + standings.
  getTournament: (pid: string, tid: string) =>
    request<TournamentView>("GET", `/portal/projects/${seg(pid)}/tournaments/${seg(tid)}`),

  // Freeze an ACTIVE tournament (pauses player reporting).
  freezeTournament: (pid: string, tid: string) =>
    request<unknown>("POST", `/portal/projects/${seg(pid)}/tournaments/${seg(tid)}/freeze`, {}),

  // Unfreeze a FROZEN tournament.
  unfreezeTournament: (pid: string, tid: string) =>
    request<unknown>("POST", `/portal/projects/${seg(pid)}/tournaments/${seg(tid)}/unfreeze`, {}),

  // Admin-override a match result (requires a reason). Returns the updated tournament view.
  overrideMatch: (pid: string, tid: string, mid: string, score: TKScore, reason: string) =>
    request<TournamentView>(
      "POST",
      `/portal/projects/${seg(pid)}/tournaments/${seg(tid)}/matches/${seg(mid)}/override`,
      { score, reason }
    ),

  // The tournament's audit log, newest first.
  getAudit: (pid: string, tid: string) =>
    request<AuditEntry[]>("GET", `/portal/projects/${seg(pid)}/tournaments/${seg(tid)}/audit`),

  // ---- keys ----

  // Rotate the project's API key; the new key is returned ONCE.
  rotateKey: (pid: string) =>
    request<RotateKeyResponse>("POST", `/portal/projects/${seg(pid)}/keys/rotate`, {}),
};
