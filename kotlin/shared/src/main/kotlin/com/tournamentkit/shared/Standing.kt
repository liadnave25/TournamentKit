package com.tournamentkit.shared

import kotlinx.serialization.Serializable

// One row of a league table: a participant's running record and points.
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
