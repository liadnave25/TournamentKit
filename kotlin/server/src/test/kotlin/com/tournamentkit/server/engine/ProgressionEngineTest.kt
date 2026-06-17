package com.tournamentkit.server.engine

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Scoring
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.TKScore
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProgressionEngineTest {

    private fun players(n: Int): List<Participant> =
        (1..n).map { Participant(userId = "p$it", displayName = "Player $it") }

    private val knockoutRules =
        Template("t", TemplateType.KNOCKOUT, Scoring(3, 1, 0), 16)
    private val leagueRules =
        Template("t", TemplateType.LEAGUE, Scoring(3, 1, 0), 16)

    // Confirms a league match between home/away with the given score.
    private fun confirmed(id: String, home: String, away: String, h: Int, a: Int): Match =
        Match(id, 1, 0, home, away, TKScore(h, a), MatchStatus.CONFIRMED, null)

    // ---------- Knockout progression ----------

    @Test
    fun knockout_4_players_advances_then_finishes() {
        val bracket = drawKnockout(players(4), Random(42))
        val r1s0 = bracket.first { it.id == "r1-s0" }
        val r1s1 = bracket.first { it.id == "r1-s1" }

        // Report r1-s0 (home wins): winner advances into r2-s0 on the HOME side (even source slot).
        val res0 = progressKnockout(r1s0, TKScore(2, 1), bracket)
        assertEquals(MatchStatus.CONFIRMED, res0.updatedMatch.status)
        assertFalse(res0.tournamentFinished)
        val adv0 = res0.advancement!!
        assertEquals("r2-s0", adv0.targetMatchId)
        assertTrue("even source slot advances as home", adv0.asHome)
        assertEquals(r1s0.homeId, adv0.userId)

        // Report r1-s1 (away wins): winner advances into r2-s0 on the AWAY side (odd source slot).
        val res1 = progressKnockout(r1s1, TKScore(0, 3), bracket)
        val adv1 = res1.advancement!!
        assertEquals("r2-s0", adv1.targetMatchId)
        assertFalse("odd source slot advances as away", adv1.asHome)
        assertEquals(r1s1.awayId, adv1.userId)

        // Both finalists known: report the final -> tournament finished, no advancement.
        val finalSeed = bracket.first { it.id == "r2-s0" }
            .copy(homeId = adv0.userId, awayId = adv1.userId)
        val resF = progressKnockout(finalSeed, TKScore(1, 0), bracket)
        assertTrue(resF.tournamentFinished)
        assertNull(resF.advancement)
        assertEquals(MatchStatus.CONFIRMED, resF.updatedMatch.status)
    }

    @Test
    fun knockout_bye_round2_progresses_when_other_side_arrives() {
        val bracket = drawKnockout(players(5), Random(42))
        // Find a round-2 match that already has one side pre-filled by a BYE.
        val r2 = bracket.filter { it.round == 2 }
        val preFilled = r2.first { it.homeId != DrawEngine.TBD_ID || it.awayId != DrawEngine.TBD_ID }

        // Find a round-1 real match that feeds this round-2 match and report it.
        val feeder = bracket.first {
            it.round == 1 && it.nextMatchId == preFilled.id && it.awayId != null
        }
        val res = progressKnockout(feeder, TKScore(3, 0), bracket)
        val adv = res.advancement!!
        assertEquals(preFilled.id, adv.targetMatchId)
        assertEquals(feeder.homeId, adv.userId)   // home scored 3-0
    }

    @Test
    fun knockout_draw_is_invalid_score() {
        val bracket = drawKnockout(players(4), Random(42))
        val r1s0 = bracket.first { it.id == "r1-s0" }
        expectCode(TKErrorCode.TK_INVALID_SCORE) { progressKnockout(r1s0, TKScore(1, 1), bracket) }
    }

    @Test
    fun reporting_confirmed_match_is_rejected() {
        val bracket = drawKnockout(players(4), Random(42))
        val r1s0 = bracket.first { it.id == "r1-s0" }.copy(status = MatchStatus.CONFIRMED)
        expectCode(TKErrorCode.TK_MATCH_ALREADY_REPORTED) { progressKnockout(r1s0, TKScore(2, 1), bracket) }
    }

    @Test
    fun negative_score_is_invalid() {
        val bracket = drawKnockout(players(4), Random(42))
        val r1s0 = bracket.first { it.id == "r1-s0" }
        expectCode(TKErrorCode.TK_INVALID_SCORE) { progressKnockout(r1s0, TKScore(-1, 2), bracket) }
    }

    // ---------- League standings ----------

    @Test
    fun league_4_players_exact_table() {
        val matches = listOf(
            confirmed("m1", "p1", "p2", 2, 0),  // p1 beats p2
            confirmed("m2", "p1", "p3", 1, 0),  // p1 beats p3
            confirmed("m3", "p1", "p4", 1, 1),  // p1 draws p4
            confirmed("m4", "p2", "p3", 3, 1),  // p2 beats p3
            confirmed("m5", "p2", "p4", 0, 0),  // p2 draws p4
            confirmed("m6", "p3", "p4", 2, 1)   // p3 beats p4
        )
        val table = recalcStandings(players(4), matches, leagueRules).associateBy { it.userId }

        assertRow(table.getValue("p1"), played = 3, w = 2, d = 1, l = 0, pf = 4, pa = 1, pts = 7)
        assertRow(table.getValue("p2"), played = 3, w = 1, d = 1, l = 1, pf = 3, pa = 3, pts = 4)
        assertRow(table.getValue("p3"), played = 3, w = 1, d = 0, l = 2, pf = 3, pa = 5, pts = 3)
        assertRow(table.getValue("p4"), played = 3, w = 0, d = 2, l = 1, pf = 2, pa = 3, pts = 2)

        // Sorted order reflects points (no ties here).
        val order = recalcStandings(players(4), matches, leagueRules).map { it.userId }
        assertEquals(listOf("p1", "p2", "p3", "p4"), order)
    }

    @Test
    fun tiebreak_goal_difference() {
        // p1 and p2 both 3 pts (one win each); p1 has the better goal difference.
        val matches = listOf(
            confirmed("m1", "p1", "p3", 5, 0),  // p1 +5
            confirmed("m2", "p2", "p3", 1, 0)   // p2 +1
        )
        val order = recalcStandings(players(3), matches, leagueRules).map { it.userId }
        assertEquals("better difference ranks first", "p1", order.first())
    }

    @Test
    fun tiebreak_points_for_when_difference_equal() {
        // p1 and p2: same points (1 win each) and same difference (+1), but p1 scored more.
        val matches = listOf(
            confirmed("m1", "p1", "p3", 3, 2),  // p1 +1, PF 3
            confirmed("m2", "p2", "p4", 1, 0)   // p2 +1, PF 1
        )
        val order = recalcStandings(players(4), matches, leagueRules).map { it.userId }
        assertEquals("higher pointsFor ranks first", "p1", order.first())
    }

    @Test
    fun tiebreak_head_to_head() {
        // p1, p2, p3 are tied on points (6), difference (0) AND pointsFor (2); two donors (p4,p5)
        // hand out the wins/losses that equalize them. Head-to-head then decides: p1 beat both,
        // p2 beat p3, p3 beat neither -> p1 must rank ahead of p2 (and p2 ahead of p3).
        val matches = listOf(
            // in-set games (all 1-0): p1>p2, p1>p3, p2>p3
            confirmed("m1", "p1", "p2", 1, 0),
            confirmed("m2", "p1", "p3", 1, 0),
            confirmed("m3", "p2", "p3", 1, 0),
            // donor games equalize overall points/diff/pointsFor across the trio
            confirmed("m4", "p4", "p1", 1, 0),  // p1 loses to p4
            confirmed("m5", "p5", "p1", 1, 0),  // p1 loses to p5
            confirmed("m6", "p2", "p4", 1, 0),  // p2 beats p4
            confirmed("m7", "p5", "p2", 1, 0),  // p2 loses to p5
            confirmed("m8", "p3", "p4", 1, 0),  // p3 beats p4
            confirmed("m9", "p3", "p5", 1, 0)   // p3 beats p5
        )
        val players5 = (1..5).map { Participant("p$it", "Player $it") }
        val order = recalcStandings(players5, matches, leagueRules).map { it.userId }
        assertTrue("p1 ahead of p2 on head-to-head", order.indexOf("p1") < order.indexOf("p2"))
        assertTrue("p2 ahead of p3 on head-to-head", order.indexOf("p2") < order.indexOf("p3"))
    }

    @Test
    fun tiebreak_userid_fallback_is_deterministic() {
        // p2 and p1 are completely identical; lexicographic userId breaks the tie (p1 before p2).
        val matches = listOf(
            confirmed("m1", "p1", "p3", 1, 0),
            confirmed("m2", "p2", "p4", 1, 0)
            // no p1-vs-p2 game, mirror-image stats
        )
        val order = recalcStandings(players(4), matches, leagueRules).map { it.userId }
        assertTrue("deterministic userId fallback", order.indexOf("p1") < order.indexOf("p2"))
    }

    @Test
    fun league_finished_only_after_last_match() {
        val all = drawLeague(players(4), Random(42))   // 6 scheduled matches
        // Confirm all but one.
        val confirmedMatches = all.dropLast(1).map { it.copy(score = TKScore(1, 0), status = MatchStatus.CONFIRMED) }
        assertFalse(isLeagueFinished(all, confirmedMatches))

        val allConfirmed = all.map { it.copy(score = TKScore(1, 0), status = MatchStatus.CONFIRMED) }
        assertTrue(isLeagueFinished(all, allConfirmed))
    }

    // ---------- Groups -> knockout bridge ----------

    @Test
    fun groups_bridge_builds_cross_seeded_bracket() {
        // Two groups of 4 with scripted, fully-confirmed round robins.
        val groupA = players(4)                                    // p1..p4
        val groupB = (5..8).map { Participant("p$it", "Player $it") } // p5..p8
        val groups = GroupsDraw(
            groups = listOf(
                Group(0, groupA, roundRobinWhereFirstWinsAll(groupA)),
                Group(1, groupB, roundRobinWhereFirstWinsAll(groupB))
            )
        )
        val standings = mapOf(
            "0" to recalcStandings(groupA, groups.groups[0].matches, leagueRules),
            "1" to recalcStandings(groupB, groups.groups[1].matches, leagueRules)
        )
        val bracket = buildKnockoutFromGroups(groups, standings, qualifiersPerGroup = 2)

        // 2 groups x 2 qualifiers = 4 -> a 4-player bracket (2 semis + 1 final).
        assertEquals(3, bracket.size)
        val r1 = bracket.filter { it.round == 1 }.sortedBy { it.slot }
        assertEquals(2, r1.size)

        // Cross seeding: 1A vs 2B and 1B vs 2A. Group A winner is p1, runner-up the next; B winner p5.
        val winnerA = standings.getValue("0").first().userId   // p1
        val runnerA = standings.getValue("0")[1].userId
        val winnerB = standings.getValue("1").first().userId   // p5
        val runnerB = standings.getValue("1")[1].userId

        val pairs = r1.map { setOf(it.homeId, it.awayId) }.toSet()
        assertTrue("1A vs 2B present", setOf(winnerA, runnerB) in pairs)
        assertTrue("1B vs 2A present", setOf(winnerB, runnerA) in pairs)

        // Valid tree: each non-final points to an existing match; final points nowhere.
        val byId = bracket.associateBy { it.id }
        for (m in bracket) {
            if (m.nextMatchId == null) assertEquals(2, m.round) else assertTrue(byId.containsKey(m.nextMatchId))
        }
    }

    @Test
    fun groups_bridge_rejects_non_power_of_two() {
        val groups = threeGroupsAllConfirmed()
        val standings = groups.groups.associate { it.index.toString() to recalcStandings(it.participants, it.matches, leagueRules) }
        // 3 groups x 1 qualifier = 3 -> not a power of 2.
        try {
            buildKnockoutFromGroups(groups, standings, qualifiersPerGroup = 1)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("power of 2", ignoreCase = true))
        }
    }

    @Test
    fun groups_bridge_rejects_unfinished_group() {
        val groupA = players(4)
        val groupB = (5..8).map { Participant("p$it", "Player $it") }
        // Group B left with a PENDING match.
        val bMatches = roundRobinWhereFirstWinsAll(groupB).toMutableList()
        bMatches[0] = bMatches[0].copy(status = MatchStatus.PENDING, score = null)
        val groups = GroupsDraw(listOf(Group(0, groupA, roundRobinWhereFirstWinsAll(groupA)), Group(1, groupB, bMatches)))
        val standings = mapOf(
            "0" to recalcStandings(groupA, groups.groups[0].matches, leagueRules),
            "1" to recalcStandings(groupB, bMatches.filter { it.status == MatchStatus.CONFIRMED }, leagueRules)
        )
        try {
            buildKnockoutFromGroups(groups, standings, qualifiersPerGroup = 2)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("confirmed", ignoreCase = true))
        }
    }

    // ---------- helpers ----------

    // Builds a fully-confirmed round robin where the first listed player beats everyone, others lose all.
    private fun roundRobinWhereFirstWinsAll(group: List<Participant>): List<Match> {
        val ms = ArrayList<Match>()
        var i = 0
        for (a in group.indices) {
            for (b in a + 1 until group.size) {
                // Earlier-listed player wins, by a margin that decreases down the list (keeps a clean order).
                val home = group[a].userId
                val away = group[b].userId
                ms.add(Match("x${i++}", 1, 0, home, away, TKScore(group.size - a, 0), MatchStatus.CONFIRMED, null))
            }
        }
        return ms
    }

    private fun threeGroupsAllConfirmed(): GroupsDraw {
        val g0 = (1..3).map { Participant("a$it", "A$it") }
        val g1 = (1..3).map { Participant("b$it", "B$it") }
        val g2 = (1..3).map { Participant("c$it", "C$it") }
        return GroupsDraw(
            listOf(
                Group(0, g0, roundRobinWhereFirstWinsAll(g0)),
                Group(1, g1, roundRobinWhereFirstWinsAll(g1)),
                Group(2, g2, roundRobinWhereFirstWinsAll(g2))
            )
        )
    }

    private fun assertRow(s: com.tournamentkit.shared.Standing, played: Int, w: Int, d: Int, l: Int, pf: Int, pa: Int, pts: Int) {
        assertEquals("played", played, s.played)
        assertEquals("won", w, s.won)
        assertEquals("drawn", d, s.drawn)
        assertEquals("lost", l, s.lost)
        assertEquals("pointsFor", pf, s.pointsFor)
        assertEquals("pointsAgainst", pa, s.pointsAgainst)
        assertEquals("points", pts, s.points)
    }

    private fun expectCode(code: TKErrorCode, block: () -> Unit) {
        try {
            block()
            fail("expected TKException with code $code")
        } catch (e: TKException) {
            assertEquals(code, e.code)
        }
    }
}
