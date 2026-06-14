# TournamentKit — Agent Rules

## GIT POLICY (HIGHEST PRIORITY)
You must NEVER run git write commands (`add`, `commit`, `push`, `reset`, `checkout`,
`restore`, `stash`, `rebase`, `merge`) or any `gh` command. The user manages all
version control manually. Read-only git (`status`, `diff`, `log`) is allowed.
When a task is done — stop and report. Do NOT commit.

## What this project is
TournamentKit: an Android SDK (Kotlin) that gives any app a full tournament/league
engine, backed by a Ktor server (Cloud Run) + Firestore + a React management portal.
Full spec: `docs/tournamentkit-spec.md` — it is the source of truth for requirements.

## Repo map & ownership
```
kotlin/            All Kotlin. One Gradle build.
  shared/          Pure Kotlin data models (Tournament, Match, Standing, Template,
                   Participant, TKScore, TKError). kotlinx.serialization. NO Android deps.
  server/          Ktor + Firebase Admin SDK. ALL game logic lives here.
  sdk/             Android library (AAR). Public API per spec section 3.
  demo-app/        FifaNight demo. Uses ONLY the SDK public API.
portal/            React + Vite + Firebase Hosting. Auth via Firebase ID Token.
firebase/          firestore.rules, firebase.json, emulator config.
docs/              Spec, portal-api.md, implementation plan.
```
- Kotlin tasks: write only inside `kotlin/`. Portal tasks: write only inside `portal/`.
- Reading any file anywhere in the repo (e.g. server routes, shared models) is allowed
  and encouraged to understand contracts.

## Architecture invariants (never violate)
1. The client NEVER writes to Firestore. Every mutation goes through the Ktor server
   (Admin SDK). Firestore Security Rules block all client writes.
2. The client uses Firestore ONLY for read-only snapshot listeners (real-time).
3. reportResult on the server updates Match + both Standings + next bracket match in
   a SINGLE Firestore Transaction.
4. Every public SDK callback returns success or a typed `TKError` (codes in spec §3).
5. Game logic (draw, progression, ELO) is implemented as pure functions in
   `kotlin/server` with unit tests — no I/O inside the engines.

## Conventions
- Every function gets ONE short comment line above it explaining what it does.
  No long doc blocks. The user must be able to read and understand the entire
  codebase — prefer simple, readable code over clever code.
- End every task with a brief code walkthrough so the user can own what was written.
- Targeted diffs, not rewrites. Touch the minimum number of files.
- All scripts/commands must be Windows-compatible (PowerShell / .cmd).
- Local development runs against the Firebase Emulator Suite, never production.
- New endpoints → update `docs/portal-api.md` in the same task.

## Common commands
```
# Firebase emulator (run from repo root)
firebase emulators:start

# Kotlin (run from kotlin/)
.\gradlew.bat :server:test          # engine unit tests
.\gradlew.bat :server:run           # Ktor locally (expects FIRESTORE_EMULATOR_HOST=localhost:8080)
.\gradlew.bat :sdk:assembleRelease  # build AAR

# Portal (run from portal/)
npm run dev
```

## Definition of done for any task
- Code compiles; relevant tests pass.
- No git commands were run.
- Short summary of changed files reported to the user, who will review and commit manually.
