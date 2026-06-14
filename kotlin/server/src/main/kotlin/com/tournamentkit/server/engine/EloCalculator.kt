package com.tournamentkit.server.engine

import kotlin.math.pow
import kotlin.math.roundToInt

// Computes cumulative player ratings with the standard ELO formula. Pure math, no I/O.
object EloCalculator {
    // Every player starts here until they have played a rated game.
    const val DEFAULT_RATING: Int = 1200
}

// Returns the new (winner, loser) ratings after one game (isDraw=true treats it as a tie).
fun updateRatings(
    winnerRating: Int,
    loserRating: Int,
    isDraw: Boolean,
    kFactor: Int = 32
): Pair<Int, Int> {
    // Expected score for the "winner" slot: chance of winning given the rating gap.
    val expectedWinner = 1.0 / (1.0 + 10.0.pow((loserRating - winnerRating) / 400.0))
    val expectedLoser = 1.0 - expectedWinner

    // Actual score: 0.5/0.5 on a draw, otherwise 1 for the winner and 0 for the loser.
    val actualWinner = if (isDraw) 0.5 else 1.0
    val actualLoser = if (isDraw) 0.5 else 0.0

    // new = old + K * (actual - expected), rounded to the nearest whole rating point.
    val newWinner = (winnerRating + kFactor * (actualWinner - expectedWinner)).roundToInt()
    val newLoser = (loserRating + kFactor * (actualLoser - expectedLoser)).roundToInt()
    return newWinner to newLoser
}
