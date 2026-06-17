package com.tournamentkit.shared

import kotlinx.serialization.Serializable

// The kind of competition a template produces.
@Serializable
enum class TemplateType {
    KNOCKOUT,
    LEAGUE,
    GROUPS_KNOCKOUT,
    // Open-ended points leaderboard: no matches, no draw, no bracket — one op adds points to a person.
    TALLY
}

// Points awarded for a win, a draw, and a loss.
@Serializable
data class Scoring(
    val win: Int,
    val draw: Int,
    val loss: Int
)

// A reusable set of rules (defined in the portal) that tournaments are created from.
@Serializable
data class Template(
    val id: String,
    val type: TemplateType,
    val scoring: Scoring,
    val maxParticipants: Int
)
