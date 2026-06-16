package com.tournamentkit.shared

import kotlinx.serialization.Serializable

// One row of a league table: a participant's running record and points.
// For a TALLY competition only `userId` and `points` are meaningful (points is the running total,
// which may be negative); the match-derived fields (played/won/drawn/lost/pointsFor/pointsAgainst) stay 0.
@Serializable
data class Standing(
    val userId: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val points: Int
)
