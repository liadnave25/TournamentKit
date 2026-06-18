package com.tournamentkit.sdk.net

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import kotlinx.serialization.Serializable

// Internal wire DTOs for the /v1 endpoints, mirroring the server's bodies (the public API exposes only :shared models).

// ---------- request bodies ----------

@Serializable
internal data class CreateTournamentBody(
    val templateId: String,
    val name: String,
    val userId: String,
    val displayName: String
)

@Serializable
internal data class JoinBody(
    val joinCode: String,
    val userId: String,
    val displayName: String
)

@Serializable
internal data class StartBody(val userId: String)

@Serializable
internal data class ReportBody(
    val tournamentId: String,
    val matchId: String,
    val userId: String,
    val score: TKScore
)

@Serializable
internal data class TallyAddBody(
    val tournamentId: String,
    val userId: String,
    val displayName: String,
    val points: Int
)

// ---------- response bodies ----------

// The server's full tournament view (tournament + matches + standings), which the SDK unwraps per call.
@Serializable
internal data class TournamentViewDto(
    val tournament: Tournament,
    val matches: List<Match>,
    val standings: List<Standing>
)

// Server's rating response.
@Serializable
internal data class RatingDto(val userId: String, val rating: Int)
