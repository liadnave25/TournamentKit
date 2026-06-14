package com.tournamentkit.server.engine

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import kotlin.random.Random

// Holds engine-wide constants for the tournament draw.
object DrawEngine {
    // Placeholder id for a BYE: an empty round-1 slot that auto-advances its single real player.
    const val BYE_ID: String = "BYE"

    // Placeholder id for a future match slot not yet decided (a winner will fill it later).
    const val TBD_ID: String = ""
}

// ---------- Validation ----------

// Throws if the participant list is too small, has duplicates, or uses the reserved BYE id.
private fun requireValidParticipants(participants: List<Participant>) {
    require(participants.size >= 2) { "a draw needs at least 2 participants" }
    val ids = participants.map { it.userId }
    require(ids.none { it == DrawEngine.BYE_ID }) { "userId \"${DrawEngine.BYE_ID}\" is reserved" }
    require(ids.size == ids.distinct().size) { "participants contain duplicate userIds" }
}

// ---------- Knockout (Single Elimination) ----------

// Draws a single-elimination bracket: full match tree with forward pointers, BYEs auto-advanced.
fun drawKnockout(participants: List<Participant>, random: Random = Random.Default): List<Match> {
    requireValidParticipants(participants)

    val bracketSize = nextPowerOfTwo(participants.size)
    val leaves = placeIntoBracket(participants, bracketSize, random)  // size == bracketSize, BYE_ID for empty slots
    return buildBracketTree(leaves)
}

// Builds the full knockout tree from ordered leaf ids (BYE_ID allowed); wires forward pointers and pre-advances BYEs.
internal fun buildBracketTree(leaves: List<String>): List<Match> {
    val bracketSize = leaves.size
    val rounds = roundCount(bracketSize)                      // e.g. 8 -> 3
    val matchesByRound = ArrayList<MutableList<Match>>()

    // Round 1: pair adjacent leaves (slot s uses leaves 2s and 2s+1).
    val round1 = ArrayList<Match>()
    val round1Count = bracketSize / 2
    for (slot in 0 until round1Count) {
        val home = leaves[2 * slot]
        val away = leaves[2 * slot + 1]
        round1.add(buildLeafMatch(round = 1, slot = slot, home = home, away = away, lastRound = rounds))
    }
    matchesByRound.add(round1)

    // Rounds 2..final: empty matches for now; players arrive when earlier rounds resolve.
    var prevCount = round1Count
    for (round in 2..rounds) {
        val count = prevCount / 2
        val list = ArrayList<Match>()
        for (slot in 0 until count) {
            list.add(
                Match(
                    id = matchId(round, slot),
                    round = round,
                    slot = slot,
                    homeId = DrawEngine.TBD_ID,   // filled as winners advance from earlier rounds
                    awayId = DrawEngine.TBD_ID,
                    score = null,
                    status = MatchStatus.PENDING,
                    nextMatchId = if (round == rounds) null else matchId(round + 1, slot / 2)
                )
            )
        }
        matchesByRound.add(list)
        prevCount = count
    }

    // Pre-advance BYE winners: a round-1 match with only one real player feeds that player forward now.
    val flat = matchesByRound.flatten().associateBy { it.id }.toMutableMap()
    for (m in matchesByRound[0]) {
        if (m.status == MatchStatus.CONFIRMED && m.nextMatchId != null) {
            val target = flat.getValue(m.nextMatchId!!)
            flat[target.id] = placeWinnerInto(target, m.slot, m.homeId)
        }
    }

    // Return matches ordered by round then slot for stable, readable output.
    return flat.values.sortedWith(compareBy({ it.round }, { it.slot }))
}

// Builds a round-1 match, auto-confirming it if one side is a BYE.
private fun buildLeafMatch(round: Int, slot: Int, home: String, away: String, lastRound: Int): Match {
    val homeIsBye = home == DrawEngine.BYE_ID
    val awayIsBye = away == DrawEngine.BYE_ID
    val next = if (round == lastRound) null else matchId(round + 1, slot / 2)

    // A real-vs-real match is a normal pending game.
    if (!homeIsBye && !awayIsBye) {
        return Match(matchId(round, slot), round, slot, home, away, null, MatchStatus.PENDING, next)
    }
    // Exactly one real player: present them as home, no opponent, already confirmed (auto-advance).
    val realPlayer = if (homeIsBye) away else home
    return Match(matchId(round, slot), round, slot, realPlayer, null, null, MatchStatus.CONFIRMED, next)
}

// True if a winner from [sourceSlot] advances onto the HOME side of the next match (even slot -> home).
internal fun winnerGoesHome(sourceSlot: Int): Boolean = sourceSlot % 2 == 0

// Returns a copy of [target] with [winner] placed on the side matching the source slot's parity.
internal fun placeWinnerInto(target: Match, sourceSlot: Int, winner: String): Match {
    // Even source slot feeds the home side of the next match; odd feeds the away side.
    return if (winnerGoesHome(sourceSlot)) target.copy(homeId = winner) else target.copy(awayId = winner)
}

// Builds the ordered bracket leaves (length == bracketSize), filling empty positions with BYE_ID.
private fun placeIntoBracket(participants: List<Participant>, bracketSize: Int, random: Random): List<String> {
    // Order entrants best-to-worst: seeded players by seed ascending, then unseeded shuffled.
    val seeded = participants.filter { it.seed != null }.sortedBy { it.seed }
    val unseeded = participants.filter { it.seed == null }.shuffled(random)
    val ordered = seeded + unseeded

    // Standard bracket: position p holds seed rank seedRanks[p]; best entrants go to rank 1,2,...
    val seedRanks = seedRankOrder(bracketSize)
    val slots = arrayOfNulls<String>(bracketSize)
    for (p in 0 until bracketSize) {
        val rank = seedRanks[p]                 // 1-based seed rank assigned to this position
        slots[p] = if (rank <= ordered.size) ordered[rank - 1].userId else DrawEngine.BYE_ID
    }
    return slots.map { it!! }
}

// Returns, for each bracket position, the 1-based seed rank that standard seeding places there.
private fun seedRankOrder(bracketSize: Int): IntArray {
    // Classic fold: [1] -> [1,2] -> [1,4,3,2] -> [1,8,5,4,3,6,7,2] ... keeps top seeds far apart.
    var order = listOf(1)
    var size = 1
    while (size < bracketSize) {
        val next = ArrayList<Int>(size * 2)
        val sum = size * 2 + 1
        for (x in order) {
            next.add(x)
            next.add(sum - x)
        }
        order = next
        size *= 2
    }
    return order.toIntArray()
}

// ---------- League (Round Robin) ----------

// Draws a single round robin where everyone plays everyone once, balanced via the circle method.
fun drawLeague(participants: List<Participant>, random: Random = Random.Default): List<Match> {
    requireValidParticipants(participants)

    // Shuffle for a fair random draw, then add a BYE filler if the count is odd.
    val ids = participants.map { it.userId }.shuffled(random).toMutableList()
    if (ids.size % 2 != 0) ids.add(DrawEngine.BYE_ID)

    val n = ids.size
    val roundsTotal = n - 1
    val half = n / 2
    val matches = ArrayList<Match>()

    // Circle method: fix ids[0], rotate the rest each round; pair ends toward the middle.
    val ring = ids.toMutableList()
    for (round in 1..roundsTotal) {
        var slot = 0
        for (i in 0 until half) {
            val home = ring[i]
            val away = ring[n - 1 - i]
            // A pairing that includes the BYE filler produces no real match.
            if (home != DrawEngine.BYE_ID && away != DrawEngine.BYE_ID) {
                matches.add(
                    Match(
                        id = "g${round}-s${slot}",
                        round = round,
                        slot = slot,
                        homeId = home,
                        awayId = away,
                        score = null,
                        status = MatchStatus.PENDING,
                        nextMatchId = null   // league matches never advance anyone
                    )
                )
                slot++
            }
        }
        rotate(ring)
    }
    return matches
}

// Rotates the ring in place keeping the first element fixed (standard circle-method step).
private fun rotate(ring: MutableList<String>) {
    if (ring.size <= 2) return
    val last = ring.removeAt(ring.size - 1)
    ring.add(1, last)
}

// ---------- Groups + Knockout ----------

// Draws balanced groups (each a round robin); the knockout stage is built later from group results.
fun drawGroups(participants: List<Participant>, groupCount: Int, random: Random = Random.Default): GroupsDraw {
    requireValidParticipants(participants)
    require(groupCount in 2..participants.size) { "group count must be between 2 and the participant count" }

    // Order entrants so seeded players are spread across groups pot-style; unseeded shuffled after.
    val seeded = participants.filter { it.seed != null }.sortedBy { it.seed }
    val unseeded = participants.filter { it.seed == null }.shuffled(random)
    val ordered = seeded + unseeded

    // Snake/pot distribution: deal one entrant at a time across groups so sizes differ by at most 1.
    val buckets = Array(groupCount) { ArrayList<Participant>() }
    ordered.forEachIndexed { i, p -> buckets[i % groupCount].add(p) }

    // Each group plays its own round robin (reuse the league draw, seeded by the same Random).
    val groups = buckets.mapIndexed { index, members ->
        Group(index = index, participants = members, matches = drawLeague(members, random))
    }
    return GroupsDraw(groups = groups)
}

// ---------- Small math helpers ----------

// Smallest power of two that is >= n (the bracket size for a knockout).
private fun nextPowerOfTwo(n: Int): Int {
    var size = 1
    while (size < n) size *= 2
    return size
}

// Number of knockout rounds for a bracket of the given size (log2).
private fun roundCount(bracketSize: Int): Int {
    var rounds = 0
    var size = bracketSize
    while (size > 1) {
        size /= 2
        rounds++
    }
    return rounds
}

// Readable, deterministic match id like "r2-s1" (round, slot).
private fun matchId(round: Int, slot: Int): String = "r$round-s$slot"
