package com.tournamentkit.shared

import kotlinx.serialization.Serializable

// The result of a match: goals/points for each side.
@Serializable
data class TKScore(
    val home: Int,
    val away: Int
)

// Where a match stands: not yet played (PENDING) or final result entered (CONFIRMED).
// Single-writer model: a reported result is final immediately — there is no intermediate state.
@Serializable
enum class MatchStatus {
    PENDING,
    CONFIRMED
}

// A single game between two participants, plus a pointer to where the winner advances.
@Serializable
data class Match(
    val id: String,
    val round: Int,
    val slot: Int,
    val homeId: String,
    val awayId: String? = null,
    val score: TKScore? = null,
    val status: MatchStatus,
    val nextMatchId: String? = null
)
