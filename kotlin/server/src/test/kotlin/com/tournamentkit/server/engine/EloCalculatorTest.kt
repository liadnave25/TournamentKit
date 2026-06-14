package com.tournamentkit.server.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EloCalculatorTest {

    @Test
    fun equal_ratings_win_is_plus_minus_16() {
        val (winner, loser) = updateRatings(1200, 1200, isDraw = false, kFactor = 32)
        assertEquals(1216, winner)
        assertEquals(1184, loser)
    }

    @Test
    fun underdog_win_gains_more_than_a_favorite_would() {
        // Underdog (1200) beats favorite (1400): gain should exceed the 16 of an even match.
        val (underdogNew, _) = updateRatings(winnerRating = 1200, loserRating = 1400, isDraw = false)
        val gain = underdogNew - 1200
        assertTrue("underdog win should gain more than 16, got $gain", gain > 16)
    }

    @Test
    fun draw_between_unequal_ratings_moves_toward_each_other() {
        // On a draw, the lower-rated player gains and the higher-rated loses.
        // Pass the higher rating as "winner" slot; isDraw ignores who is which.
        val (higherNew, lowerNew) = updateRatings(winnerRating = 1400, loserRating = 1200, isDraw = true)
        assertTrue("higher-rated loses points on a draw", higherNew < 1400)
        assertTrue("lower-rated gains points on a draw", lowerNew > 1200)
    }

    @Test
    fun points_gained_equals_points_lost_within_rounding() {
        val (winner, loser) = updateRatings(1300, 1250, isDraw = false)
        val gained = winner - 1300
        val lost = 1250 - loser
        assertTrue("zero-sum within rounding", kotlin.math.abs(gained - lost) <= 1)
    }

    @Test
    fun default_starting_rating_is_1200() {
        assertEquals(1200, EloCalculator.DEFAULT_RATING)
    }
}
