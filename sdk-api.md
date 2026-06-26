# TournamentKit — SDK Reference

Complete reference for every public function, data class, and error code.

---

## Setup

These two functions are **synchronous** (no callback, no network call).

### `init`

```kotlin
TournamentKit.init(
    context      : Context,
    apiKey       : String,
    projectId    : String,
    baseUrl      : String  = "https://tournamentkit-server-…",  // optional
    debugLogging : Boolean = false                               // optional
)
```

Must be called once before any other SDK function (e.g. in `Application.onCreate`).

- `baseUrl` — override to point at a self-hosted server; defaults to the managed Cloud Run instance.
- `debugLogging` — prints HTTP request/response details to Logcat.

**Example** — a football app initializes the SDK once for the whole app lifecycle:
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize once so every Activity can use TournamentKit immediately.
        TournamentKit.init(
            context      = this,
            apiKey       = BuildConfig.TK_API_KEY,
            projectId    = "proj-myfootball",
            debugLogging = BuildConfig.DEBUG
        )
    }
}
```

---

### `identify`

```kotlin
TournamentKit.identify(userId: String, displayName: String)
```

Records the current app user. Must be called before any function that acts on behalf of a player
(`joinTournament`, `startTournament`, `reportResult`, `getUserRating`).

**Example** — a chess app identifies the user after they log in, so every subsequent SDK call
knows who is acting:
```kotlin
fun onUserLoggedIn(firebaseUser: FirebaseUser) {
    // Tell the SDK who the current player is before any tournament interaction.
    TournamentKit.identify(
        userId      = firebaseUser.uid,
        displayName = firebaseUser.displayName ?: "Anonymous"
    )
}
```

---

## Tournament lifecycle

All functions below are **asynchronous**: they take a `TKCallback<T>` and deliver the result on the **main thread**.

### `createTournament`

```kotlin
TournamentKit.createTournament(
    templateId : String,
    name       : String,
    callback   : TKCallback<Tournament>
)
```

Creates a new tournament from a portal template. The calling user is auto-joined as the creator.
Returns the tournament in `REGISTRATION` status with a 6-character `joinCode`.

| Possible error | Cause |
|---|---|
| `TK_TOURNAMENT_NOT_FOUND` | `templateId` does not exist in this project |

**Example** — a padel app lets the organizer create a new weekly knockout tournament and
immediately shows the join code to share with friends:
```kotlin
TournamentKit.createTournament(
    templateId = "tmpl-knockout-8",
    name       = "Friday Padel Cup",
    callback   = object : TKCallback<Tournament> {
        override fun onSuccess(result: Tournament) {
            // Show the join code so other players can join.
            tvJoinCode.text = "Join code: ${result.joinCode}"
        }
        override fun onError(error: TKError) {
            showError(error.message)
        }
    }
)
```

---

### `getOrCreateTournament`

```kotlin
TournamentKit.getOrCreateTournament(
    externalKey : String,
    templateId  : String,
    name        : String,
    callback    : TKCallback<Tournament>
)
```

Idempotent version of `createTournament`. On the first call, creates a tournament and saves its id
locally under `externalKey`. On subsequent calls, returns the saved tournament without creating a new one.
Use a different `externalKey` per tournament slot in your app.

| Possible error | Cause |
|---|---|
| `TK_INVALID_ARGUMENT` | `externalKey` is blank |
| `TK_TOURNAMENT_NOT_FOUND` | `templateId` does not exist |

**Example** — a trivia app runs one league per week. Using `getOrCreateTournament` ensures
that reopening the app mid-week re-joins the existing tournament instead of creating a duplicate:
```kotlin
// "week_2025_06" is a stable key for this week's slot — same key = same tournament.
TournamentKit.getOrCreateTournament(
    externalKey = "week_2025_06",
    templateId  = "tmpl-league",
    name        = "Week 6 Trivia League",
    callback    = object : TKCallback<Tournament> {
        override fun onSuccess(result: Tournament) {
            navigateToLobby(result)
        }
        override fun onError(error: TKError) { showError(error.message) }
    }
)
```

---

### `joinTournament`

```kotlin
TournamentKit.joinTournament(joinCode: String, callback: TKCallback<Participant>)
```

Joins a `REGISTRATION`-status tournament by its join code. Returns the caller's `Participant` entry.

| Possible error | Cause |
|---|---|
| `TK_TOURNAMENT_NOT_FOUND` | no tournament with that join code |
| `TK_TOURNAMENT_FULL` | participant cap reached |
| `TK_ALREADY_JOINED` | user already in this tournament |
| `TK_TOURNAMENT_LOCKED` | tournament is not in `REGISTRATION` |

**Example** — a gym app shows a code-entry screen where members type in the code the
trainer shared. Once joined, the app navigates to the waiting lobby:
```kotlin
btnJoin.setOnClickListener {
    val code = etJoinCode.text.toString().trim()
    TournamentKit.joinTournament(
        joinCode = code,
        callback = object : TKCallback<Participant> {
            override fun onSuccess(result: Participant) {
                // result.displayName confirms who just joined.
                navigateToLobby()
            }
            override fun onError(error: TKError) {
                if (error.code == TKErrorCode.TK_TOURNAMENT_FULL)
                    showError("This tournament is full.")
                else
                    showError(error.message)
            }
        }
    )
}
```

---

### `startTournament`

```kotlin
TournamentKit.startTournament(tournamentId: String, callback: TKCallback<Tournament>)
```

Locks registration, runs the draw engine (byes included), and writes all matches in one batch.
Only the creator (first participant) may call this. Returns the tournament now in `ACTIVE` status.

> **TALLY** tournaments are already `ACTIVE` from creation — calling `startTournament` on one is a no-op that returns it unchanged.

| Possible error | Cause |
|---|---|
| `TK_NOT_AUTHENTICATED` | calling user is not the creator |
| `TK_TOURNAMENT_LOCKED` | tournament is not in `REGISTRATION` |
| `TK_INVALID_SCORE` | fewer than 2 participants |

**Example** — the organizer presses "Start" once everyone has joined. The server draws the
bracket and the app navigates all participants to the bracket screen:
```kotlin
btnStart.setOnClickListener {
    TournamentKit.startTournament(
        tournamentId = currentTournamentId,
        callback     = object : TKCallback<Tournament> {
            override fun onSuccess(result: Tournament) {
                // Bracket is ready — navigate to it.
                navigateToBracket(result.id)
            }
            override fun onError(error: TKError) {
                if (error.code == TKErrorCode.TK_NOT_AUTHENTICATED)
                    showError("Only the creator can start the tournament.")
                else
                    showError(error.message)
            }
        }
    )
}
```

---

### `clearSession` / `clearAllSessions`

```kotlin
TournamentKit.clearSession(externalKey: String)   // forgets one saved tournament id
TournamentKit.clearAllSessions()                  // forgets every saved tournament id
```

Synchronous. No network call. Useful when the same device should start a fresh tournament for a
given slot (e.g. at the start of a new season).

**Example** — at the start of a new season the app resets all saved tournament slots so
`getOrCreateTournament` will create fresh ones instead of reusing last season's:
```kotlin
fun onNewSeasonStarted() {
    // Wipe every saved tournament id — next getOrCreateTournament call creates fresh ones.
    TournamentKit.clearAllSessions()
    Toast.makeText(this, "New season started!", Toast.LENGTH_SHORT).show()
}
```

---

## Match results & scoring

### `reportResult`

```kotlin
TournamentKit.reportResult(
    tournamentId : String,
    matchId      : String,
    score        : TKScore,
    callback     : TKCallback<Match>
)
```

Reports a match result. The result is **confirmed immediately** in a single Firestore transaction —
there is no separate approval step. The server simultaneously:
- marks the match `CONFIRMED`
- advances the winner into the next bracket slot (Knockout)
- recomputes the standings table (League / Groups)
- updates both players' ELO ratings
- marks the tournament `FINISHED` if this was the last match

Returns the confirmed `Match`.

| Possible error | Cause |
|---|---|
| `TK_NOT_PARTICIPANT` | calling user is not `homeId` or `awayId` |
| `TK_MATCH_ALREADY_REPORTED` | match is already `CONFIRMED` |
| `TK_TOURNAMENT_FROZEN` | an admin froze the tournament |
| `TK_INVALID_SCORE` | negative values, or a draw in a Knockout match |
| `TK_NOT_SUPPORTED_FOR_TYPE` | tournament type is `TALLY` (use `addScore` instead) |

**Example** — after a football match the winning player opens the app and submits the
score. The bracket updates for everyone in real time:
```kotlin
TournamentKit.reportResult(
    tournamentId = currentTournamentId,
    matchId      = currentMatch.id,
    score        = TKScore(home = 3, away = 1),
    callback     = object : TKCallback<Match> {
        override fun onSuccess(result: Match) {
            // Match is CONFIRMED — the bracket advances automatically on the server.
            showToast("Result submitted! Next round updated.")
        }
        override fun onError(error: TKError) {
            when (error.code) {
                TKErrorCode.TK_MATCH_ALREADY_REPORTED -> showError("Score already submitted.")
                TKErrorCode.TK_TOURNAMENT_FROZEN      -> showError("Tournament is paused by admin.")
                else                                   -> showError(error.message)
            }
        }
    }
)
```

---

### `addScore` *(TALLY only)*

```kotlin
TournamentKit.addScore(
    tournamentId : String,
    userId       : String,
    displayName  : String,
    points       : Int,
    callback     : TKCallback<Standing>
)
```

Adds `points` to a participant on a Tally leaderboard. The participant is **auto-created** on their
first call. `points` may be negative (corrections are allowed; totals may go below zero). Returns
the updated `Standing` for that user.

| Possible error | Cause |
|---|---|
| `TK_NOT_SUPPORTED_FOR_TYPE` | tournament is not `TALLY` |
| `TK_TOURNAMENT_FROZEN` | tournament is frozen |

**Example** — a quiz app awards points after each question. The trainer also uses a
negative value to correct a scoring mistake from the previous round:
```kotlin
// Award 10 points for a correct answer.
TournamentKit.addScore(
    tournamentId = leaderboardId,
    userId       = "user_42",
    displayName  = "Liad",
    points       = 10,
    callback     = object : TKCallback<Standing> {
        override fun onSuccess(result: Standing) {
            tvTotal.text = "Total: ${result.points} pts"
        }
        override fun onError(error: TKError) { showError(error.message) }
    }
)

// Correct a mistake: subtract 5 points.
TournamentKit.addScore(tournamentId = leaderboardId, userId = "user_42",
    displayName = "Liad", points = -5, callback = …)
```

---

## Queries

### `getTournament`

```kotlin
TournamentKit.getTournament(tournamentId: String, callback: TKCallback<TournamentView>)
```

Returns the full tournament state: `TournamentView(tournament, matches, standings)`.

**Example** — the bracket screen loads the full tournament state on entry and passes
the matches to the UI component:
```kotlin
TournamentKit.getTournament(
    tournamentId = tournamentId,
    callback     = object : TKCallback<TournamentView> {
        override fun onSuccess(result: TournamentView) {
            // Pass matches to the Compose bracket component.
            bracketState.value = result.matches
        }
        override fun onError(error: TKError) { showError(error.message) }
    }
)
```

---

### `getStandings`

```kotlin
TournamentKit.getStandings(tournamentId: String, callback: TKCallback<List<Standing>>)
```

Returns standings sorted by the engine's tiebreaker chain:
`points → goal difference → goals for → head-to-head → userId`.
For **TALLY** tournaments: sorted by `points` descending, then `userId` as stable tiebreak.

**Example** — a league app refreshes the standings table after the user pulls down to
refresh, showing the current rank for every team:
```kotlin
swipeRefresh.setOnRefreshListener {
    TournamentKit.getStandings(
        tournamentId = leagueId,
        callback     = object : TKCallback<List<Standing>> {
            override fun onSuccess(result: List<Standing>) {
                swipeRefresh.isRefreshing = false
                // result is already sorted — bind directly to the RecyclerView adapter.
                standingsAdapter.submitList(result)
            }
            override fun onError(error: TKError) {
                swipeRefresh.isRefreshing = false
                showError(error.message)
            }
        }
    )
}
```

---

### `getUserRating`

```kotlin
TournamentKit.getUserRating(callback: TKCallback<Int>)
```

Returns the identified user's cumulative ELO rating for this project. Defaults to `1200` if the
user has never played a decisive match.

**Example** — a profile screen shows the player's current ELO alongside their win/loss
history to give context for their skill level:
```kotlin
TournamentKit.getUserRating(object : TKCallback<Int> {
    override fun onSuccess(result: Int) {
        // Display rating with a label — 1200 is the starting baseline.
        tvRating.text = "ELO Rating: $result"
    }
    override fun onError(error: TKError) { showError(error.message) }
})
```

---

## Callback interface

```kotlin
interface TKCallback<T> {
    fun onSuccess(result: T)        // always called on the main thread
    fun onError(error: TKError)     // always called on the main thread
}

data class TKError(val code: TKErrorCode, val message: String)
```

**Example** — a reusable helper that shows a loading spinner while waiting and dismisses
it on both success and error:
```kotlin
fun <T> loadingCallback(
    onSuccess: (T) -> Unit
): TKCallback<T> = object : TKCallback<T> {
    override fun onSuccess(result: T) {
        progressBar.isVisible = false
        onSuccess(result)
    }
    override fun onError(error: TKError) {
        progressBar.isVisible = false
        Snackbar.make(rootView, error.message, Snackbar.LENGTH_LONG).show()
    }
}

// Usage:
TournamentKit.getUserRating(loadingCallback { rating -> tvRating.text = "ELO: $rating" })
```

---

## Data classes

### `Tournament`

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Unique tournament id |
| `projectId` | `String` | Owning project |
| `templateId` | `String` | Source template (rules are snapshotted — see `rules`) |
| `name` | `String` | Display name |
| `joinCode` | `String` | 6-character code for joining |
| `status` | `TournamentStatus` | `REGISTRATION` → `ACTIVE` → `FINISHED` (or `FROZEN`) |
| `participants` | `List<Participant>` | Enrolled players in join order |
| `rules` | `Template` | Snapshot of the template at creation time |
| `createdAt` | `Long` | Unix ms |
| `startedAt` | `Long?` | Unix ms, null until started |

**Example** — reading a `Tournament` object to decide which screen to show:
```kotlin
// Use the status field to route the user to the right screen.
when (tournament.status) {
    TournamentStatus.REGISTRATION -> showLobby(tournament.joinCode)
    TournamentStatus.ACTIVE       -> showBracket(tournament.id)
    TournamentStatus.FINISHED     -> showResults(tournament.id)
    TournamentStatus.FROZEN       -> showFrozenBanner()
}
```

---

### `Participant`

| Field | Type |
|---|---|
| `userId` | `String` |
| `displayName` | `String` |
| `avatarUrl` | `String?` |
| `seed` | `Int` |

**Example** — building a participant list in the lobby screen from the tournament's
participants field:
```kotlin
// Map participants to display rows for the lobby RecyclerView.
val rows = tournament.participants.map { p ->
    LobbyRow(name = p.displayName, seed = p.seed)
}
lobbyAdapter.submitList(rows)
```

---

### `Match`

| Field | Type | Description |
|---|---|---|
| `id` | `String` | e.g. `"r1-s0"` (round 1, slot 0) |
| `round` | `Int` | |
| `slot` | `Int` | Position within the round |
| `homeId` | `String` | userId |
| `awayId` | `String?` | null = BYE |
| `score` | `TKScore?` | null until reported |
| `status` | `MatchStatus` | `PENDING` or `CONFIRMED` |
| `nextMatchId` | `String?` | Bracket advancement target |

**Example** — filtering the match list to show only the current user's pending matches:
```kotlin
// Find the matches the signed-in user still needs to play.
val myPendingMatches = matches.filter { match ->
    match.status == MatchStatus.PENDING &&
    (match.homeId == currentUserId || match.awayId == currentUserId)
}
```

---

### `Standing`

| Field | Type |
|---|---|
| `userId` | `String` |
| `played` | `Int` |
| `won` | `Int` |
| `drawn` | `Int` |
| `lost` | `Int` |
| `pointsFor` | `Int` |
| `pointsAgainst` | `Int` |
| `points` | `Int` |

**Example** — displaying a standing row in a league table with goal difference:
```kotlin
// Compute goal difference on the fly from pointsFor and pointsAgainst.
val gd = standing.pointsFor - standing.pointsAgainst
tvRow.text = "${standing.won}W ${standing.drawn}D ${standing.lost}L  GD:$gd  Pts:${standing.points}"
```

---

### `TKScore`

```kotlin
data class TKScore(val home: Int, val away: Int)
```

**Example** — building a score from user input in a result-entry dialog:
```kotlin
// Read the two EditTexts and wrap them in a TKScore before calling reportResult.
val score = TKScore(
    home = etHomeScore.text.toString().toInt(),
    away = etAwayScore.text.toString().toInt()
)
TournamentKit.reportResult(tournamentId, matchId, score, callback)
```

---

### `Template`

| Field | Type | Values |
|---|---|---|
| `id` | `String` | |
| `type` | `TemplateType` | `KNOCKOUT`, `LEAGUE`, `GROUPS_KNOCKOUT`, `TALLY` |
| `scoring` | `Scoring` | |
| `maxParticipants` | `Int` | |

**Example** — using the snapshotted `rules` from a `Tournament` to decide which UI
component to render:
```kotlin
// The rules field is a snapshot — safe to read even after the template is edited.
when (tournament.rules.type) {
    TemplateType.KNOCKOUT        -> showBracketView(matches)
    TemplateType.LEAGUE          -> showLeagueTable(standings)
    TemplateType.GROUPS_KNOCKOUT -> showGroupsAndBracket(matches, standings)
    TemplateType.TALLY           -> showLeaderboard(standings)
}
```

---

### `Scoring`

```kotlin
data class Scoring(val win: Int, val draw: Int, val loss: Int)
```

**Example** — showing the point system to players before a league starts so they
understand how points are awarded:
```kotlin
val s = tournament.rules.scoring
tvScoringInfo.text = "Win: ${s.win} pts  |  Draw: ${s.draw} pts  |  Loss: ${s.loss} pts"
```

---

## Error codes

| Code | When |
|---|---|
| `TK_NOT_INITIALIZED` | Any call before `init` |
| `TK_INVALID_ARGUMENT` | Blank `externalKey` or other malformed input |
| `TK_NOT_AUTHENTICATED` | Bad API key / project id, or non-creator calling `startTournament` |
| `TK_FORBIDDEN` | Portal-only: caller does not own the project |
| `TK_TOURNAMENT_NOT_FOUND` | Bad `tournamentId`, `templateId`, or `matchId` |
| `TK_TOURNAMENT_FULL` | Participant cap (`maxParticipants`) reached |
| `TK_ALREADY_JOINED` | User already in this tournament |
| `TK_TOURNAMENT_LOCKED` | Tournament is not in the required status for the operation |
| `TK_TOURNAMENT_FROZEN` | `reportResult` or `addScore` while tournament is frozen |
| `TK_MATCH_ALREADY_REPORTED` | Match is already `CONFIRMED` |
| `TK_INVALID_SCORE` | Negative score, draw in a Knockout match, or other validation failure |
| `TK_NOT_SUPPORTED_FOR_TYPE` | Operation does not apply to this tournament type |
| `TK_NOT_PARTICIPANT` | Acting user is not `homeId` or `awayId` of the match |
| `TK_RATE_LIMITED` | Too many requests from this API key / IP |
| `TK_NETWORK_ERROR` | Connectivity failure (client-side) |
| `TK_UNKNOWN` | Unexpected server error |
