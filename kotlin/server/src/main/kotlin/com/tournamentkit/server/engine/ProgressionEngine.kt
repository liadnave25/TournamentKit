package com.tournamentkit.server.engine

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.TKScore

// ---------- Winner determination ----------

// Validates a reported score against the match state and rules, then decides the outcome.
fun decideWinner(match: Match, score: TKScore, rules: Template): WinnerDecision {
    // Scores can never be negative.
    if (score.home < 0 || score.away < 0) {
        throw TKException(TKErrorCode.TK_INVALID_SCORE, "scores must be non-negative")
    }
    // A confirmed match cannot be reported again.
    if (match.status == MatchStatus.CONFIRMED) {
        throw TKException(TKErrorCode.TK_MATCH_ALREADY_REPORTED, "match ${match.id} is already confirmed")
    }
    // Knockout needs a decisive result — a draw cannot advance anyone.
    if (rules.type == TemplateType.KNOCKOUT && score.home == score.away) {
        throw TKException(TKErrorCode.TK_INVALID_SCORE, "knockout matches cannot end in a draw")
    }
    return when {
        score.home > score.away -> WinnerDecision(Outcome.HOME_WIN, match.homeId, match.awayId)
        score.away > score.home -> WinnerDecision(Outcome.AWAY_WIN, match.awayId, match.homeId)
        else -> WinnerDecision(Outcome.DRAW, null, null)
    }
}

// ---------- Knockout progression ----------

// Confirms a knockout match and reports who advances where (or that the tournament is finished).
fun progressKnockout(match: Match, score: TKScore, allMatches: List<Match>): ProgressionResult {
    // Knockout always uses decisive scoring; build a throwaway rule just to reuse decideWinner's checks.
    val knockoutRules = Template("", TemplateType.KNOCKOUT, com.tournamentkit.shared.Scoring(3, 1, 0), 0)
    val decision = decideWinner(match, score, knockoutRules)
    val winnerId = decision.winnerId!!   // never null in knockout (draws are rejected above)

    val confirmed = match.copy(score = score, status = MatchStatus.CONFIRMED)

    // The final has no next match -> the tournament ends here.
    if (match.nextMatchId == null) {
        return ProgressionResult(updatedMatch = confirmed, advancement = null, tournamentFinished = true)
    }

    // Otherwise the winner advances; the side is decided by this match's slot parity (same rule as the draw).
    val advancement = Advancement(
        targetMatchId = match.nextMatchId!!,
        asHome = winnerGoesHome(match.slot),
        userId = winnerId
    )
    return ProgressionResult(updatedMatch = confirmed, advancement = advancement, tournamentFinished = false)
}

// ---------- League / groups standings ----------

// Recomputes the full league table from all CONFIRMED matches, sorted by the spec tiebreaker chain.
fun recalcStandings(
    participants: List<Participant>,
    confirmedMatches: List<Match>,
    rules: Template
): List<Standing> {
    val confirmed = confirmedMatches.filter { it.status == MatchStatus.CONFIRMED && it.score != null }

    // Start every participant at an empty row.
    val rows = participants.associate { it.userId to MutableRow() }

    // Fold each confirmed match into both players' rows.
    for (m in confirmed) {
        val s = m.score!!
        val home = rows[m.homeId] ?: continue
        val away = rows[m.awayId] ?: continue
        applyResult(home, scored = s.home, conceded = s.away, rules = rules)
        applyResult(away, scored = s.away, conceded = s.home, rules = rules)
    }

    val standings = participants.map { p ->
        val r = rows.getValue(p.userId)
        Standing(p.userId, r.played, r.won, r.drawn, r.lost, r.pointsFor, r.pointsAgainst, r.points)
    }
    return sortStandings(standings, confirmed)
}

// Adds one game's outcome to a row (points come from the template's scoring).
private fun applyResult(row: MutableRow, scored: Int, conceded: Int, rules: Template) {
    row.played++
    row.pointsFor += scored
    row.pointsAgainst += conceded
    when {
        scored > conceded -> { row.won++; row.points += rules.scoring.win }
        scored == conceded -> { row.drawn++; row.points += rules.scoring.draw }
        else -> { row.lost++; row.points += rules.scoring.loss }
    }
}

// Sorts standings by the spec §6 tiebreaker chain, ending in a deterministic userId fallback.
private fun sortStandings(standings: List<Standing>, confirmed: List<Match>): List<Standing> {
    return standings.sortedWith(Comparator { a, b ->
        // 1. points (desc)
        if (a.points != b.points) return@Comparator b.points - a.points
        // 2. goal/point difference (desc)
        val diffA = a.pointsFor - a.pointsAgainst
        val diffB = b.pointsFor - b.pointsAgainst
        if (diffA != diffB) return@Comparator diffB - diffA
        // 3. pointsFor (desc)
        if (a.pointsFor != b.pointsFor) return@Comparator b.pointsFor - a.pointsFor
        // 4. head-to-head points among everyone currently tied with these two (desc)
        val tied = standings.filter {
            it.points == a.points &&
                (it.pointsFor - it.pointsAgainst) == diffA &&
                it.pointsFor == a.pointsFor
        }.map { it.userId }.toSet()
        val h2hA = headToHeadPoints(a.userId, tied, confirmed)
        val h2hB = headToHeadPoints(b.userId, tied, confirmed)
        if (h2hA != h2hB) return@Comparator h2hB - h2hA
        // 5. deterministic fallback: userId lexicographic — guarantees a total order, no ambiguity.
        a.userId.compareTo(b.userId)
    })
}

// Points a player earned only in matches against the given tied set (3/1/0 from win/draw/loss).
private fun headToHeadPoints(userId: String, tiedSet: Set<String>, confirmed: List<Match>): Int {
    var pts = 0
    for (m in confirmed) {
        val s = m.score ?: continue
        // Only count games where BOTH players are in the tied set.
        if (m.homeId !in tiedSet || m.awayId !in tiedSet) continue
        val (mine, theirs) = when (userId) {
            m.homeId -> s.home to s.away
            m.awayId -> s.away to s.home
            else -> continue
        }
        pts += if (mine > theirs) 3 else if (mine == theirs) 1 else 0
    }
    return pts
}

// True when every scheduled league match has been confirmed.
fun isLeagueFinished(scheduledMatches: List<Match>, confirmedMatches: List<Match>): Boolean {
    val confirmedIds = confirmedMatches.filter { it.status == MatchStatus.CONFIRMED }.map { it.id }.toSet()
    return scheduledMatches.all { it.id in confirmedIds }
}

// A mutable scratch row used while folding match results into a table.
private class MutableRow {
    var played = 0
    var won = 0
    var drawn = 0
    var lost = 0
    var pointsFor = 0
    var pointsAgainst = 0
    var points = 0
}

// ---------- Groups -> knockout bridge ----------

// Builds the knockout bracket from finished groups using cross seeding (winners vs other groups' runners-up).
fun buildKnockoutFromGroups(
    groups: GroupsDraw,
    standings: Map<String, List<Standing>>,
    qualifiersPerGroup: Int
): List<Match> {
    // Every group must be fully played before we can know the qualifiers.
    for (g in groups.groups) {
        require(g.matches.all { it.status == MatchStatus.CONFIRMED }) {
            "group ${g.index} has matches that are not confirmed"
        }
    }

    val groupCount = groups.groups.size
    val totalQualifiers = groupCount * qualifiersPerGroup
    // A clean post-group bracket has no BYEs, so the qualifier count must be a power of 2 >= 2.
    require(totalQualifiers >= 2 && isPowerOfTwo(totalQualifiers)) {
        "total qualifiers ($totalQualifiers) must be a power of 2"
    }

    // Top N userIds per group, in final-table order.
    val qualifiers = groups.groups.associate { g ->
        val table = standings.getValue(g.index.toString())
        g.index to table.take(qualifiersPerGroup).map { it.userId }
    }

    // Cross-seeding pairs: each group i's rank-r qualifier faces the adjacent group (i XOR 1)'s mirror rank.
    // For 2 qualifiers: 1A vs 2B, 1B vs 2A, 1C vs 2D, 1D vs 2C — winners meet other groups' runners-up.
    val leaves = ArrayList<String>()
    for (i in 0 until groupCount step 2) {
        val gA = i
        val gB = i + 1
        for (rank in 0 until qualifiersPerGroup) {
            // Pair group A's rank-r player with group B's mirror-rank player (and vice versa).
            val mirror = qualifiersPerGroup - 1 - rank
            leaves.add(qualifiers.getValue(gA)[rank])
            leaves.add(qualifiers.getValue(gB)[mirror])
        }
    }

    // Reuse the DrawEngine tree builder so round/slot/nextMatchId math lives in exactly one place.
    return buildBracketTree(leaves)
}

// True if n is a power of two (n & (n-1) == 0 for positive n).
private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0
