# TournamentKit — Portal

The multi-tenant web admin for TournamentKit. Developers sign in (Firebase Email/Password)
and manage their own projects: dashboards, templates, tournaments, and API keys. The portal
talks **only** to the server's `/portal/*` HTTP API (never to Firestore directly) and authenticates
every request with the Firebase ID token as `Authorization: Bearer`.

Stack: React + TypeScript + Vite, Firebase JS SDK (Auth only), React Router, a small typed
fetch client, and a hand-rolled `useAsync` data hook.

## Setup

```bash
cd portal
npm install
cp .env.example .env     # then fill in the values (see below)
```

### Environment variables (`portal/.env`)

`.env` is gitignored; `.env.example` lists the names. All values are **public** Firebase web config
(not secrets — security is enforced by the server's ownership checks, not by hiding config).

- `VITE_API_BASE_URL` — the server. Omit to use the deployed Cloud Run server (default). For local
  dev point it at your local server, e.g. `http://localhost:8090`.
- `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`,
  `VITE_FIREBASE_APP_ID` — the web app config from the Firebase Console for project
  `tournamentkit-e098e` (Project settings → Your apps → Web app).
- `VITE_FIREBASE_AUTH_EMULATOR` — optional; set to `http://localhost:9099` to use the Auth emulator.

## Run

```bash
npm run dev      # http://localhost:5173
npm run build    # type-check (tsc) + production build to dist/
npm run preview  # preview the production build
```

## Which server / auth to use

**Option A — deployed server + real Firebase Auth (simplest):**
Leave `VITE_API_BASE_URL` at the default (or set it to the Cloud Run URL) and use the real Firebase
web config with `VITE_FIREBASE_AUTH_EMULATOR` unset. Sign up with a real email/password.

**Option B — local server + Auth emulator (offline dev):**
1. From the repo root, start the emulators (Auth on 9099, Firestore on 8080):
   `firebase emulators:start --project tournamentkit-e098e`
2. From `kotlin/`, run the server against the emulators on port 8090:
   ```powershell
   $env:FIRESTORE_EMULATOR_HOST     = "localhost:8080"
   $env:FIREBASE_AUTH_EMULATOR_HOST = "localhost:9099"
   $env:GCP_PROJECT                 = "tournamentkit-e098e"
   $env:PORT                        = "8090"
   .\gradlew.bat :server:run
   ```
   (The server's `GCP_PROJECT` must match the emulator's project so it can verify the ID token.)
3. In `portal/.env` set `VITE_API_BASE_URL=http://localhost:8090` and
   `VITE_FIREBASE_AUTH_EMULATOR=http://localhost:9099`, then `npm run dev`.

> The portal never needs `google-services.json` and never reaches Firestore — only `/portal/*` HTTP.

## What's here (session 11)

App shell + Floodlight theme, auth (login/sign-up), create-first-project with a show-once API key,
a project switcher, and the **Dashboard** (live analytics for the selected project). Templates,
tournaments, keys, and analytics-in-depth come in session 12 and reuse the same theme tokens and
API client.
