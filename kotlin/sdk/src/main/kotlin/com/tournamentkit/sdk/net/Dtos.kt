package com.tournamentkit.sdk.net

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import kotlinx.serialization.Serializable

// Wire DTOs for the /v1 endpoints. These stay INTERNAL to the SDK — the public API only ever
// exposes :shared models (Tournament, Match, Standing, Participant). Bodies mirror the server's DTOs.

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
internal data class ConfirmBody(
    val tournamentId: String,
    val matchId: String,
    val userId: String
)

@Serializable
internal data class TallyAddBody(
    val tournamentId: String,
    val userId: String,
    val displayName: String,
    val points: Int
)

// ---------- response bodies ----------

// Server's full tournament view (tournament + matches + standings). The SDK unwraps the part each call needs.
@Serializable
internal data class TournamentViewDto(
    val tournament: Tournament,
    val matches: List<Match>,
    val standings: List<Standing>
)

// Server's rating response.
@Serializable
internal data class RatingDto(val userId: String, val rating: Int)
