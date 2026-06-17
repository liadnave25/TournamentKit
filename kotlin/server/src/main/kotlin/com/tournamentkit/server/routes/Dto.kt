package com.tournamentkit.server.routes

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import kotlinx.serialization.Serializable

// ---------- request bodies ----------

// POST /v1/tournaments — the creator auto-joins the tournament they create.
@Serializable
data class CreateTournamentRequest(
    val templateId: String,
    val name: String,
    val userId: String,
    val displayName: String
)

// POST /v1/tournaments/join
@Serializable
data class JoinRequest(
    val joinCode: String,
    val userId: String,
    val displayName: String
)

// POST /v1/tournaments/{id}/start
@Serializable
data class StartRequest(val userId: String)

// POST /v1/matches/report
@Serializable
data class ReportRequest(
    val tournamentId: String,
    val matchId: String,
    val userId: String,
    val score: TKScore
)

// POST /v1/tally/add — add points to a person on a TALLY leaderboard (points may be negative).
@Serializable
data class TallyAddRequest(
    val tournamentId: String,
    val userId: String,
    val displayName: String,
    val points: Int
)

// ---------- response bodies ----------

// Full tournament view: the tournament plus its matches and standings.
@Serializable
data class TournamentView(
    val tournament: Tournament,
    val matches: List<Match>,
    val standings: List<Standing>
)

// A user's ELO rating in the current project.
@Serializable
data class RatingView(val userId: String, val rating: Int)

// Result of POST /dev/seed.
@Serializable
data class SeedResponse(
    val projectId: String,
    val apiKey: String,
    val knockoutTemplateId: String,
    val leagueTemplateId: String
)
