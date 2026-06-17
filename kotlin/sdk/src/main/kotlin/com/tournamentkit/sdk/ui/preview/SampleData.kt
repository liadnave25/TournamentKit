package com.tournamentkit.sdk.ui.preview

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TKScore

// Realistic sample data shared by @Preview composables and the layout unit tests, so what you see in
// Android Studio is exactly what the math is tested against. Mirrors the engine's id/round/slot scheme.
object SampleData {

    // A pool of display names; previews resolve "p1".."pN" against this.
    private val NAMES = listOf(
        "Dani", "Ron", "Guy", "Maya", "Noa", "Omri", "Tal", "Yael",
        "Avi", "Lior", "Shira", "Eitan", "Dana", "Roi", "Gal", "Niv"
    )

    // A name resolver for previews: "p3" -> the 3rd sample name (falls back to the raw id).
    val nameOf: (String) -> String = { id ->
        id.removePrefix("p").toIntOrNull()?.let { NAMES.getOrNull(it - 1) } ?: id
    }

    // ---- single match examples (one per state) ----

    val matchPending = Match("r1-s0", 1, 0, "p1", "p2", null, MatchStatus.PENDING, "r2-s0")
    // A played match: in the single-writer model a reported result is final (CONFIRMED) immediately.
    val matchReported = Match("r1-s1", 1, 1, "p3", "p4", TKScore(2, 1), MatchStatus.CONFIRMED, "r2-s0")
    val matchConfirmed = Match("r1-s2", 1, 2, "p5", "p6", TKScore(3, 0), MatchStatus.CONFIRMED, "r2-s1")
    val matchBye = Match("r1-s3", 1, 3, "p7", null, null, MatchStatus.CONFIRMED, "r2-s1")
    val matchTbd = Match("r2-s0", 2, 0, "", "", null, MatchStatus.PENDING, "r3-s0")

    // ---- standings (engine-sorted order assumed) ----

    val standings: List<Standing> = listOf(
        Standing("p1", 5, 4, 1, 0, 14, 4, 13),
        Standing("p2", 5, 3, 1, 1, 10, 6, 10),
        Standing("p3", 5, 2, 2, 1, 9, 7, 8),
        Standing("p4", 5, 2, 1, 2, 8, 9, 7),
        Standing("p5", 5, 1, 1, 3, 5, 11, 4),
        Standing("p6", 5, 0, 0, 5, 3, 15, 0)
    )

    // ---- brackets ----

    // A clean N-player bracket (N a power of 2): every round real-vs-real, round 1 fully played.
    fun bracket(players: Int): List<Match> {
        val out = ArrayList<Match>()
        var slotsThisRound = players / 2
        var round = 1
        var nextPlayer = 1
        while (slotsThisRound >= 1) {
            val lastRound = slotsThisRound == 1
            for (slot in 0 until slotsThisRound) {
                val next = if (lastRound) null else "r${round + 1}-s${slot / 2}"
                if (round == 1) {
                    // Round 1: real players, the first match decided so a winner shows.
                    val home = "p${nextPlayer++}"; val away = "p${nextPlayer++}"
                    val confirmed = slot == 0
                    out.add(Match("r$round-s$slot", round, slot, home, away,
                        if (confirmed) TKScore(2, 1) else null,
                        if (confirmed) MatchStatus.CONFIRMED else MatchStatus.PENDING, next))
                } else {
                    // Later rounds start as awaiting-opponent slots.
                    out.add(Match("r$round-s$slot", round, slot, "", "", null, MatchStatus.PENDING, next))
                }
            }
            slotsThisRound /= 2
            round++
        }
        return out
    }

    // A 5-player bracket in an 8-slot tree: 3 BYEs (auto-advanced), one real round-1 match.
    fun bracketWithByes(): List<Match> {
        // round 1: s0 real-vs-real, s1..s3 are byes (one real player, no opponent, auto-confirmed)
        return listOf(
            Match("r1-s0", 1, 0, "p1", "p2", TKScore(2, 1), MatchStatus.CONFIRMED, "r2-s0"),
            Match("r1-s1", 1, 1, "p3", null, null, MatchStatus.CONFIRMED, "r2-s0"),
            Match("r1-s2", 1, 2, "p4", null, null, MatchStatus.CONFIRMED, "r2-s1"),
            Match("r1-s3", 1, 3, "p5", null, null, MatchStatus.CONFIRMED, "r2-s1"),
            // round 2: byes pre-advanced their players in; s0 awaits the r1-s0 winner on home side
            Match("r2-s0", 2, 0, "", "p3", null, MatchStatus.PENDING, "r3-s0"),
            Match("r2-s1", 2, 1, "p4", "p5", null, MatchStatus.PENDING, "r3-s0"),
            // final
            Match("r3-s0", 3, 0, "", "", null, MatchStatus.PENDING, null)
        )
    }
}
