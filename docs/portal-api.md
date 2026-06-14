# TournamentKit — Server API

This documents the HTTP API exposed by the Ktor server. There are two surfaces:
the **public `/v1`** API (consumed by the Android SDK, authenticated by API key) and
the **management `/portal`** API (consumed by the React portal, authenticated by a
Firebase ID token).

## Conventions

- Base URL (local): `http://localhost:<PORT>` (default `8080`; use a different port,
  e.g. `8090`, when the Firestore emulator holds `8080`).
- All request and response bodies are JSON.
- **Public `/v1` auth** — headers on every request:
  - `X-TK-API-KEY` — the project's API key.
  - `X-TK-PROJECT-ID` — the project id.
  - The server verifies `SHA-256(apiKey) == projects/{projectId}.apiKeyHash`.
  - `userId` is supplied in the request body where relevant (the SDK's `identify`).
- **Portal `/portal` auth** — header on every request:
  - `Authorization: Bearer <firebaseIdToken>` — verified with the Firebase Admin SDK.
  - Every portal route is under `/portal/projects/{projectId}/...`; the caller must be
    the project's owner (`projects/{projectId}.ownerUid == token.uid`), else `403`.
- **CORS** is enabled for browser clients (preflight `OPTIONS` succeeds). Allowed origins come from
  the `CORS_ORIGINS` env var (comma-separated full origins, e.g.
  `CORS_ORIGINS=https://portal.example.com,http://localhost:5173`); it defaults to
  `http://localhost:5173,http://127.0.0.1:5173` for local dev. Allowed methods: GET, POST, PUT,
  DELETE, OPTIONS; allowed headers: `Authorization`, `Content-Type`, `X-TK-API-KEY`, `X-TK-PROJECT-ID`.

## Errors

Every error returns a JSON `TKError` body: `{ "code": "<TKErrorCode>", "message": "..." }`.

| Code | HTTP | Meaning |
|---|---|---|
| `TK_NOT_AUTHENTICATED` | 401 | Missing/invalid API key or project; or acting user not allowed |
| `TK_NOT_PARTICIPANT` | 401 | Acting user is not one of the match's two players |
| `TK_TOURNAMENT_NOT_FOUND` | 404 | Tournament / template / match does not exist |
| `TK_MATCH_ALREADY_REPORTED` | 409 | Match already confirmed |
| `TK_TOURNAMENT_FULL` | 409 | Participant cap (rules.maxParticipants) reached |
| `TK_ALREADY_JOINED` | 409 | User already joined this tournament |
| `TK_TOURNAMENT_LOCKED` | 409 | Tournament not in the required state (e.g. freeze a non-ACTIVE one; template in use) |
| `TK_TOURNAMENT_FROZEN` | 409 | Report/confirm rejected because the tournament is frozen |
| `TK_FORBIDDEN` | 403 | Authenticated portal user does not own this project |
| `TK_INVALID_SCORE` | 400 | Negative score, knockout draw, ineligible override, or other validation failure |
| `TK_UNKNOWN` | 500 | Unexpected server error |

---

## Public endpoints (`/v1`, SDK-facing)

### POST /v1/tournaments — create
Creates a tournament from a template; the creator auto-joins. The template's rules
are **snapshotted** into the tournament so later template edits never affect it.

Body: `{ "templateId": "...", "name": "...", "userId": "...", "displayName": "..." }`
Returns: `Tournament` (status `REGISTRATION`, with a 6-char `joinCode`).

### POST /v1/tournaments/join — join by code
Body: `{ "joinCode": "...", "userId": "...", "displayName": "..." }`
Returns: updated `Tournament`. Errors: not found, `TK_TOURNAMENT_FULL`,
`TK_ALREADY_JOINED`, `TK_TOURNAMENT_LOCKED`.

### POST /v1/tournaments/{id}/start — start
Only the creator may start; needs ≥2 participants. Runs the matching draw engine,
writes all matches (and initial standings for league/groups), sets status `ACTIVE`.

Body: `{ "userId": "..." }`
Returns: updated `Tournament`.

### POST /v1/matches/report — report a result
Runs the progression transaction (see below). If the template requires confirmation,
this only marks the match `REPORTED`; otherwise it confirms immediately.

Body: `{ "tournamentId": "...", "matchId": "...", "userId": "...", "score": { "home": 3, "away": 1 } }`
Returns: `TournamentView` (tournament + matches + standings).

### POST /v1/matches/confirm — confirm a reported result
Valid only when the template requires confirmation; the OTHER player confirms.
If confirmation is not required, returns 400.

Body: `{ "tournamentId": "...", "matchId": "...", "userId": "..." }`
Returns: `TournamentView`.

### GET /v1/tournaments/{id} — full view
Returns: `TournamentView` = `{ tournament, matches, standings }`.

### GET /v1/tournaments/{id}/standings — standings only
Returns: `List<Standing>`, sorted by the engine's tiebreaker chain
(points → difference → pointsFor → head-to-head → userId).

### GET /v1/ratings/{userId} — cumulative ELO
Returns: `{ "userId": "...", "rating": 1200 }` (default `1200` if the user has no rating yet).

---

## The reportResult transaction

`report`/`confirm` run inside **one Firestore transaction** (architecture invariant #3),
structured as **all reads first, then all writes** (a Firestore requirement):

1. Read the match; re-check status inside the transaction (race guard) — already
   confirmed → `TK_MATCH_ALREADY_REPORTED`.
2. Verify the acting user is one of the match's players.
3. Read the tournament; use its **rules snapshot** (never re-read the template).
4. Validate via `decideWinner` (knockout draws rejected).
5. Read all matches (league/groups recompute) and both players' ratings.
6. Writes: update the match → CONFIRMED; knockout: place the winner into the
   advancement target; league/groups: upsert recomputed standings; groups: when
   all group matches are confirmed, build + write the knockout stage; update both
   players' ELO; mark the tournament `FINISHED` if this result ended it.

A **frozen** tournament rejects `report`/`confirm` with `TK_TOURNAMENT_FROZEN` (the
check is the first thing the transaction does after reading the tournament). Admin
overrides bypass both the freeze check and the player-identity check.

---

## Portal endpoints (`/portal`, management UI)

All routes require `Authorization: Bearer <firebaseIdToken>`. The **project collection**
routes (`/portal/projects`) scope by the token's uid; the **per-project** routes
(`/portal/projects/{projectId}/...`) additionally require project ownership (`403` otherwise).

### Projects

**GET `/portal/projects`** — list the projects the signed-in developer owns (for the project switcher).
Response: `ProjectSummary[]` where `ProjectSummary = { id, name, createdAt }`, newest first.

**POST `/portal/projects`** — create a project owned by the caller.
Request: `{ "name": "My Game" }`. The `ownerUid` is taken from the verified token, never the body.
Response: `{ "id": "proj-1a2b3c4d", "name": "My Game", "apiKey": "tk_..." }`.
The server stores only `SHA-256(apiKey)`; **the plaintext `apiKey` is returned exactly once here and is
never retrievable again** (rotate via `/keys/rotate` to get a new one). Errors: `400` on a blank name.

### Templates

**GET `/templates`** — list all templates.
Response: `Template[]` where `Template = { id, type, scoring:{win,draw,loss}, maxParticipants, requireConfirmation, reportTimeoutHours }`, `type ∈ {KNOCKOUT, LEAGUE, GROUPS_KNOCKOUT}`.

**POST `/templates`** — create a template (id optional; generated if blank).
Request:
```json
{ "type": "KNOCKOUT", "scoring": { "win": 3, "draw": 1, "loss": 0 },
  "maxParticipants": 8, "requireConfirmation": false, "reportTimeoutHours": 24 }
```
Response: the created `Template`. Validation: scoring values ≥ 0, `maxParticipants` ≥ 2,
`reportTimeoutHours` ≥ 0, `type` a known enum (`400` otherwise).

**PUT `/templates/{templateId}`** — update a template (same body as create; id taken from the path).
Editing a template **never affects running tournaments** — their rules are snapshotted (§6).
Response: the updated `Template`.

**DELETE `/templates/{templateId}`** — delete a template.
Refuses with `409 TK_TOURNAMENT_LOCKED` if any non-FINISHED tournament still references it.
Finished tournaments may keep pointing at a deleted template (rules are snapshotted).
Response: `{ "deleted": "<templateId>" }`.

### Tournaments management

**GET `/tournaments?status=<STATUS>`** — list tournaments, newest first; optional `status` filter.
Response: summary rows `{ id, name, status, participantCount, createdAt }[]` (no match data).

**GET `/tournaments/{tournamentId}`** — full view.
Response: `{ tournament, matches, standings }` (same shape as public `GET /v1/tournaments/{id}`).

**POST `/tournaments/{tournamentId}/freeze`** — `ACTIVE → FROZEN` (pauses player reporting).
Body: none (`{}`). Response: the updated `Tournament`. `409` if not ACTIVE.

**POST `/tournaments/{tournamentId}/unfreeze`** — `FROZEN → ACTIVE`.
Body: none (`{}`). Response: the updated `Tournament`. `409` if not FROZEN.

**POST `/tournaments/{tournamentId}/matches/{matchId}/override`** — admin result override.
Request: `{ "score": { "home": 0, "away": 3 }, "reason": "scoreboard error" }` (`reason` required).
Behaves like an admin-confirmed report for a PENDING/REPORTED match. For an already-CONFIRMED
match it is allowed only if consequences have not propagated (knockout: the downstream match is
still PENDING, or it was the final; league/groups: always). Otherwise `409`.
Response: the updated full tournament view. Writes an `OVERRIDE_RESULT` audit entry.

**GET `/tournaments/{tournamentId}/audit`** — audit log, newest first.
Response: a JSON array of entries, e.g.
```json
[ { "action": "OVERRIDE_RESULT", "matchId": "r1-s0", "oldScore": {"home":2,"away":1},
    "newScore": {"home":0,"away":3}, "reason": "scoreboard error",
    "adminUid": "abc", "timestamp": 1700000000000 },
  { "action": "FREEZE", "adminUid": "abc", "timestamp": 1700000000000 } ]
```

### API keys

**POST `/keys/rotate`** — generate a new API key.
Body: none (`{}`). Response: `{ "apiKey": "tk_..." }`.
The server stores only `SHA-256(apiKey)`; **the plaintext is returned exactly once and is
never retrievable again**. The previous key immediately stops working. A `KEY_ROTATED`
entry is written to the project audit log (the key/hash are never logged).

### Analytics

**GET `/analytics`** — dashboard numbers.
Response:
```json
{ "tournamentsTotal": 12, "tournamentsByStatus": { "ACTIVE": 3, "FINISHED": 9 },
  "participantsTotal": 340, "matchesConfirmed": 870, "lastTournamentCreatedAt": 1700000000000 }
```
Computed by iterating the project's tournaments (fine at seminar scale; production would
keep a counters document).

---

## Dev-only endpoint

### POST /dev/seed — seed a known project (DEV_MODE=true only)
Creates project `dev-project` (API key `dev-key`) plus a knockout and a league
template. Optional body `{ "ownerUid": "<uid>" }` sets the project owner so a portal
smoke test can claim it. Returns `{ projectId, apiKey, knockoutTemplateId, leagueTemplateId }`.
Returns 404 when `DEV_MODE` is unset. See `kotlin/scripts/smoke-test.ps1`
(public flow) and `kotlin/scripts/portal-smoke-test.ps1` (portal flow).
