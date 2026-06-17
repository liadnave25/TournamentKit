[![](https://jitpack.io/v/liadnave25/TournamentKit.svg)](https://jitpack.io/#liadnave25/TournamentKit)

# TournamentKit

**Drop a full tournament engine into any Android app in a few lines of code.**

TournamentKit is an Android SDK for running **knockout**, **league** (round-robin), and
**groups + knockout** tournaments — registration, automatic draws & byes, result reporting,
bracket progression, standings, and cumulative ELO ratings. All the logic runs on a managed
server (Ktor + Firestore), so your app just calls the SDK; the server handles draws, scoring,
and consistency. Optional Jetpack Compose components render the bracket and tables for you.

Repo: [github.com/liadnave25/TournamentKit](https://github.com/liadnave25/TournamentKit)

---

## Install

**1.** Add JitPack to your `settings.gradle(.kts)` repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2.** Add the dependency:

```kotlin
implementation("com.github.liadnave25.TournamentKit:tournamentkit:v0.1.1")
```

**3.** The SDK talks to the server over HTTPS, so your app must declare the INTERNET permission in
`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

> You need an **API key** and a **project id** to use the SDK — create a project and get them from
> the [admin portal](#admin-portal) (see below).

---

## Quick start

The public API is **callback-based**: each call takes a `TKCallback<T>` whose `onSuccess`/`onError`
fire on the **main thread**, so you can touch UI directly.

```kotlin
import com.tournamentkit.sdk.TournamentKit
import com.tournamentkit.sdk.TKCallback
import com.tournamentkit.shared.*

// 1. Initialize once (e.g. in Application.onCreate). baseUrl defaults to the managed server.
TournamentKit.init(applicationContext, apiKey = "tk_live_…", projectId = "my-project")

// 2. Identify the current user of your app.
TournamentKit.identify(userId = "user_42", displayName = "Liad")

// 3. Get-or-create a tournament for a slot identified by your own stable externalKey.
//    The first call creates it and saves its id under that key; later calls reuse the saved id.
//    Use a DIFFERENT externalKey per tournament you manage so their saved ids never collide.
TournamentKit.getOrCreateTournament(
    externalKey = "fifa_night",
    templateId = "tmpl-knockout",
    name = "FifaNight",
    callback = object : TKCallback<Tournament> {
        override fun onSuccess(result: Tournament) {
            val id = result.id

            // 4. Start it (creator only) — the server draws the bracket.
            TournamentKit.startTournament(id, object : TKCallback<Tournament> {
                override fun onSuccess(result: Tournament) {

                    // 5. Report a match result (matchId comes from the tournament's matches).
                    TournamentKit.reportResult(
                        tournamentId = id,
                        matchId = "r1-s0",
                        score = TKScore(home = 3, away = 1),
                        callback = object : TKCallback<Match> {
                            override fun onSuccess(result: Match) { /* match is CONFIRMED */ }
                            override fun onError(error: TKError) { /* error.code + error.message */ }
                        }
                    )

                    // 6. Read the standings table.
                    TournamentKit.getStandings(id, object : TKCallback<List<Standing>> {
                        override fun onSuccess(result: List<Standing>) { /* render the table */ }
                        override fun onError(error: TKError) { }
                    })
                }
                override fun onError(error: TKError) { }
            })
        }
        override fun onError(error: TKError) { /* error is a typed TKError */ }
    }
)
```

Every failure is delivered as a typed `TKError(code, message)` (e.g. `TK_NOT_INITIALIZED`,
`TK_TOURNAMENT_FULL`, `TK_MATCH_ALREADY_REPORTED`, `TK_RATE_LIMITED`).

---

## Public API

All functions are on the `TournamentKit` object.

| Function | Returns (via `TKCallback<T>`) |
|---|---|
| `init(context, apiKey, projectId, baseUrl = …, debugLogging = false)` | — (no network; stores config) |
| `identify(userId, displayName)` | — (no network; sets the current user) |
| `createTournament(templateId, name, callback)` | `Tournament` — created in `REGISTRATION`; creator auto-joins |
| `getOrCreateTournament(externalKey, templateId, name, callback)` | `Tournament` — reuses the saved tournament for `externalKey`, or creates+saves a new one. Blank `externalKey` → `TK_INVALID_ARGUMENT` |
| `clearSession(externalKey)` | — (no network; forgets the saved tournament id for that one key) |
| `clearAllSessions()` | — (no network; forgets every saved tournament id) |
| `joinTournament(joinCode, callback)` | `Participant` — the caller's entry in the tournament |
| `startTournament(tournamentId, callback)` | `Tournament` — now `ACTIVE` with matches drawn |
| `reportResult(tournamentId, matchId, score, callback)` | `Match` — the reported match |
| `getTournament(tournamentId, callback)` | `Tournament` — current state |
| `getStandings(tournamentId, callback)` | `List<Standing>` — sorted by the engine's tiebreakers |
| `getUserRating(callback)` | `Int` — the identified user's cumulative ELO |

`init` and `identify` are synchronous (no callback); everything else is asynchronous and delivered
on the main thread. `baseUrl` defaults to the managed server but is overridable for self-hosting/testing.

---

## UI components

Optional Jetpack Compose components render tournament data with a built-in "Floodlight" theme. They
are **pure presentation** — you fetch with `getTournament`/`getStandings` and pass the data in. A
`nameOf` lambda maps a participant's `userId` to a display name.

- `BracketView(matches, nameOf, modifier)` — a left→right single-elimination bracket with connector
  lines; BYE / TBD / winner states are shown.
- `LeagueTableView(standings, nameOf, modifier)` — a standings table (rank, P, W, D, L, GD, Pts).
- `MatchCard(match, nameOf, modifier)` — one match with its status (PENDING / REPORTED / CONFIRMED).

Wrap them in `TKTheme { … }` (override the palette by passing `colors`):

```kotlin
import com.tournamentkit.sdk.ui.*

@Composable
fun Bracket(tournament: Tournament, matches: List<Match>) {
    val names = tournament.participants.associate { it.userId to it.displayName }
    TKTheme(colors = TKColors.Default.copy(primary = MyBrandGold)) {
        BracketView(matches = matches, nameOf = { id -> names[id] ?: id })
    }
}
```

---

## Admin portal

A web **admin portal** (React) accompanies the SDK for managing your projects: creating tournament
**templates**, viewing and administering **tournaments** (freeze/unfreeze, result overrides with an
audit log), browsing **analytics**, and **rotating API keys**. This is also where you create a
project and obtain the **API key + project id** that `TournamentKit.init(...)` requires.

---

## License

See the repository for license details.
