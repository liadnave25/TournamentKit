package com.tournamentkit.server.engine

import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DrawEngineTest {

    // Builds n participants p1..pn with no seeds.
    private fun players(n: Int): List<Participant> =
        (1..n).map { Participant(userId = "p$it", displayName = "Player $it") }

    // Builds n participants p1..pn each with seed = its number.
    private fun seededPlayers(n: Int): List<Participant> =
        (1..n).map { Participant(userId = "p$it", displayName = "Player $it", seed = it) }

    // ---------- Knockout ----------

    @Test
    fun knockout_8_players_has_7_matches_and_3_rounds() {
        val matches = drawKnockout(players(8), Random(42))
        assertEquals(7, matches.size)
        assertEquals(4, matches.count { it.round == 1 })
        assertEquals(2, matches.count { it.round == 2 })
        assertEquals(1, matches.count { it.round == 3 })

        val byId = matches.associateBy { it.id }
        // Every non-final match points forward to an existing match; the final points nowhere.
        for (m in matches) {
            if (m.round == 3) {
                assertNull("final must have no nextMatchId", m.nextMatchId)
            } else {
                assertTrue("non-final must point to an existing match", byId.containsKey(m.nextMatchId))
            }
        }
    }

    @Test
    fun knockout_5_players_has_3_byes_and_preadvances() {
        val matches = drawKnockout(players(5), Random(42))
        // Bracket of 8 => 4 round-1 matches; 5 real players => 3 byes.
        val round1 = matches.filter { it.round == 1 }
        assertEquals(4, round1.size)

        // A bye is a round-1 match with one real player, no opponent, already CONFIRMED.
        val byeMatches = round1.filter { it.awayId == null }
        assertEquals(3, byeMatches.size)
        for (b in byeMatches) {
            assertEquals(MatchStatus.CONFIRMED, b.status)
            assertNotEquals(DrawEngine.BYE_ID, b.homeId)  // the present player is real
        }

        // No match anywhere has two missing players.
        for (m in matches) {
            val homeMissing = m.homeId == DrawEngine.BYE_ID
            val awayMissing = m.awayId == null || m.awayId == DrawEngine.BYE_ID
            assertTrue("no match may have two empty sides", !(homeMissing && awayMissing))
        }

        // Bye'd players are pre-advanced: each bye winner appears in its round-2 target.
        val byId = matches.associateBy { it.id }
        for (b in byeMatches) {
            val next = byId.getValue(b.nextMatchId!!)
            assertTrue(
                "bye player must be pre-placed into round 2",
                next.homeId == b.homeId || next.awayId == b.homeId
            )
        }
    }

    @Test
    fun knockout_2_players_is_a_single_final() {
        val matches = drawKnockout(players(2), Random(42))
        assertEquals(1, matches.size)
        val final = matches.single()
        assertEquals(1, final.round)
        assertNull(final.nextMatchId)
        assertNotEquals(DrawEngine.BYE_ID, final.homeId)
        assertNotEquals(null, final.awayId)
    }

    @Test
    fun knockout_seeds_1_and_2_are_in_opposite_halves() {
        val matches = drawKnockout(seededPlayers(8), Random(42))
        val round1 = matches.filter { it.round == 1 }.sortedBy { it.slot }
        // Top half = slots 0..1, bottom half = slots 2..3 (each leads to a different semifinal).
        val topHalfPlayers = round1.filter { it.slot < 2 }.flatMap { listOfNotNull(it.homeId, it.awayId) }
        val bottomHalfPlayers = round1.filter { it.slot >= 2 }.flatMap { listOfNotNull(it.homeId, it.awayId) }

        val seed1InTop = "p1" in topHalfPlayers
        val seed2InTop = "p2" in topHalfPlayers
        assertNotEquals("seed 1 and seed 2 must be in opposite halves", seed1InTop, seed2InTop)
    }

    // ---------- League ----------

    @Test
    fun league_4_players_round_robin() {
        val matches = drawLeague(players(4), Random(42))
        assertEquals(6, matches.size)
        assertEquals(3, matches.map { it.round }.distinct().size)

        // Each player appears in exactly 3 matches (plays each of the other 3 once).
        for (p in players(4)) {
            val appearances = matches.count { it.homeId == p.userId || it.awayId == p.userId }
            assertEquals(3, appearances)
        }
        // No player plays twice in the same round.
        for (round in matches.map { it.round }.distinct()) {
            val ids = matches.filter { it.round == round }.flatMap { listOfNotNull(it.homeId, it.awayId) }
            assertEquals(ids.size, ids.distinct().size)
        }
        // League matches never point forward.
        assertTrue(matches.all { it.nextMatchId == null })
    }

    @Test
    fun league_5_players_odd_everyone_rests_once() {
        val matches = drawLeague(players(5), Random(42))
        // 5 players => C(5,2) = 10 matches across 5 rounds.
        assertEquals(10, matches.size)
        assertEquals(5, matches.map { it.round }.distinct().size)

        // With an odd count, each round has 2 matches (one player rests).
        for (round in 1..5) {
            assertEquals(2, matches.count { it.round == round })
        }
        // Each player plays the other 4 exactly once => 4 appearances => rests exactly once.
        for (p in players(5)) {
            val appearances = matches.count { it.homeId == p.userId || it.awayId == p.userId }
            assertEquals(4, appearances)
        }
    }

    // ---------- Groups ----------

    @Test
    fun groups_10_players_3_groups_balanced() {
        val draw = drawGroups(players(10), groupCount = 3, random = Random(42))
        assertEquals(3, draw.groups.size)
        val sizes = draw.groups.map { it.participants.size }.sorted()
        assertEquals(listOf(3, 3, 4), sizes)

        // Every participant lands in exactly one group.
        val all = draw.groups.flatMap { it.participants.map { p -> p.userId } }
        assertEquals(10, all.size)
        assertEquals(10, all.distinct().size)

        // Each group's matches are a correct single round robin for that group.
        for (g in draw.groups) {
            val n = g.participants.size
            val expected = n * (n - 1) / 2
            assertEquals(expected, g.matches.size)
            for (p in g.participants) {
                val appearances = g.matches.count { it.homeId == p.userId || it.awayId == p.userId }
                assertEquals(n - 1, appearances)
            }
        }
    }

    // ---------- Validation ----------

    @Test
    fun validation_rejects_bad_inputs() {
        assertMessage("at least 2") { drawKnockout(players(1), Random(42)) }
        assertMessage("at least 2") { drawLeague(players(1), Random(42)) }
        assertMessage("duplicate") {
            drawKnockout(listOf(Participant("x", "X"), Participant("x", "X2")), Random(42))
        }
        assertMessage("group count") { drawGroups(players(6), groupCount = 1, random = Random(42)) }
        assertMessage("group count") { drawGroups(players(3), groupCount = 5, random = Random(42)) }
    }

    // ---------- Determinism ----------

    @Test
    fun determinism_same_seed_same_draw_different_seed_differs() {
        val a = drawKnockout(players(8), Random(42))
        val b = drawKnockout(players(8), Random(42))
        assertEquals(a, b)

        val c = drawKnockout(players(8), Random(99))
        // Sanity check: a different seed should produce a different ordering of players.
        val orderA = a.filter { it.round == 1 }.sortedBy { it.slot }.map { it.homeId }
        val orderC = c.filter { it.round == 1 }.sortedBy { it.slot }.map { it.homeId }
        assertNotEquals(orderA, orderC)
    }

    // Asserts the block throws IllegalArgumentException whose message contains [needle].
    private fun assertMessage(needle: String, block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException containing \"$needle\"")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "message \"${e.message}\" should contain \"$needle\"",
                e.message?.contains(needle, ignoreCase = true) == true
            )
        }
    }
}
