package com.tournamentkit.server.data

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Scoring
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import com.tournamentkit.shared.TournamentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    // Firestore returns whole numbers as Long; simulate that by re-boxing Ints as Long before reading back.
    @Suppress("UNCHECKED_CAST")
    private fun asFirestore(map: Map<String, Any?>): Map<String, Any?> = map.mapValues { (_, v) ->
        when (v) {
            is Int -> v.toLong()
            is Map<*, *> -> asFirestore(v as Map<String, Any?>)
            is List<*> -> v.map { if (it is Map<*, *>) asFirestore(it as Map<String, Any?>) else it }
            else -> v
        }
    }

    private val template = Template("tmpl", TemplateType.LEAGUE, Scoring(3, 1, 0), 16, true, 48)

    @Test
    fun template_round_trips() {
        assertEquals(template, templateFromMap(asFirestore(template.toMap())))
    }

    @Test
    fun participant_round_trips_with_and_without_optionals() {
        val full = Participant("u1", "Alice", "http://a/x.png", 3)
        val minimal = Participant("u2", "Bob", null, null)
        assertEquals(full, participantFromMap(asFirestore(full.toMap())))
        assertEquals(minimal, participantFromMap(asFirestore(minimal.toMap())))
    }

    @Test
    fun match_round_trips_with_score_and_bye() {
        val played = Match("r1-s0", 1, 0, "u1", "u2", TKScore(2, 1), MatchStatus.CONFIRMED, "r2-s0")
        val bye = Match("r1-s1", 1, 1, "u3", null, null, MatchStatus.CONFIRMED, "r2-s0")
        assertEquals(played, matchFromMap(asFirestore(played.toMap())))
        assertEquals(bye, matchFromMap(asFirestore(bye.toMap())))
    }

    @Test
    fun standing_round_trips() {
        val s = Standing("u1", 3, 2, 1, 0, 7, 3, 7)
        assertEquals(s, standingFromMap(asFirestore(s.toMap())))
    }

    @Test
    fun tournament_round_trips_with_nested_rules_and_participants() {
        val t = Tournament(
            id = "t1",
            projectId = "p1",
            templateId = "tmpl",
            name = "FifaNight",
            joinCode = "ABC234",
            status = TournamentStatus.ACTIVE,
            participants = listOf(Participant("u1", "Alice", null, 1), Participant("u2", "Bob", null, null)),
            rules = template,
            createdAt = 1_700_000_000_000L,
            startedAt = 1_700_000_100_000L
        )
        assertEquals(t, tournamentFromMap(asFirestore(t.toMap())))
    }

    @Test
    fun tournament_round_trips_when_not_yet_started() {
        val t = Tournament("t2", "p1", "tmpl", "Open", "DEF567", TournamentStatus.REGISTRATION,
            listOf(Participant("u1", "Alice")), template, 1_700_000_000_000L, null)
        assertEquals(t, tournamentFromMap(asFirestore(t.toMap())))
    }
}
