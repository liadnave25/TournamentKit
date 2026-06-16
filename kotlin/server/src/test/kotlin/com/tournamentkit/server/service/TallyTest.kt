package com.tournamentkit.server.service

import com.tournamentkit.shared.Standing
import org.junit.Assert.assertEquals
import org.junit.Test

// Unit tests for the pure TALLY logic (no Firestore): the transaction in TallyService is a thin
// read-or-create wrapper around mergeTallyPoints, and the API ordering is tallySort.
class TallyTest {

    @Test
    fun add_to_new_user_starts_from_zero() {
        // A brand-new participant (no existing standing) gets exactly the points added.
        val s = mergeTallyPoints("u1", existing = null, points = 5)
        assertEquals("u1", s.userId)
        assertEquals(5, s.points)
        // Match-derived fields stay zero for TALLY.
        assertEquals(0, s.played)
        assertEquals(0, s.won)
        assertEquals(0, s.pointsFor)
    }

    @Test
    fun add_again_accumulates() {
        val first = mergeTallyPoints("u1", existing = null, points = 5)
        val second = mergeTallyPoints("u1", existing = first, points = 3)
        assertEquals(8, second.points)
    }

    @Test
    fun negative_points_decrease_the_total() {
        val first = mergeTallyPoints("u1", existing = null, points = 10)
        val second = mergeTallyPoints("u1", existing = first, points = -4)
        assertEquals(6, second.points)
    }

    @Test
    fun total_may_go_below_zero() {
        // TALLY is a ledger/correction, not a non-negative counter — below zero is allowed.
        val first = mergeTallyPoints("u1", existing = null, points = 2)
        val second = mergeTallyPoints("u1", existing = first, points = -5)
        assertEquals(-3, second.points)
    }

    @Test
    fun standings_sort_by_points_desc_then_userId() {
        val rows = listOf(
            Standing("bob", 0, 0, 0, 0, 0, 0, 3),
            Standing("amy", 0, 0, 0, 0, 0, 0, 10),
            Standing("cay", 0, 0, 0, 0, 0, 0, 10)  // tie with amy -> stable tiebreak by userId
        )
        val sorted = tallySort(rows)
        assertEquals(listOf("amy", "cay", "bob"), sorted.map { it.userId })
    }
}
