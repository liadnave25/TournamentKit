package com.tournamentkit.server.engine

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Standing

// The outcome of a single match for one report: who won, or whether it was a draw.
enum class Outcome { HOME_WIN, AWAY_WIN, DRAW }

// The decided result of a reported match (used by both knockout and league logic).
data class WinnerDecision(
    val outcome: Outcome,
    val winnerId: String?,   // null on a draw
    val loserId: String?     // null on a draw
)

// A knockout winner moving into a later match: where they go and on which side.
data class Advancement(
    val targetMatchId: String,
    val asHome: Boolean,
    val userId: String
)

// What a single reported result changes — engine computes this; the server layer persists it.
data class ProgressionResult(
    val updatedMatch: Match,                 // the reported match, now CONFIRMED with its score
    val advancement: Advancement? = null,    // knockout only: who moves where (null for the final)
    val updatedStandings: List<Standing>? = null,  // league/groups only: recalculated table
    val tournamentFinished: Boolean = false  // true when this result ends the tournament
)
