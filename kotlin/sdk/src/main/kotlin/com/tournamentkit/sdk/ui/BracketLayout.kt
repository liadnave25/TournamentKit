package com.tournamentkit.sdk.ui

import com.tournamentkit.shared.Match

// A computed position for one match card, in abstract pixels (Dp values are applied by the composable).
data class BracketPos(val matchId: String, val x: Float, val y: Float)

// A connector from a child match's right edge to its parent (nextMatchId) match's left edge.
data class BracketConnector(val fromId: String, val toId: String)

// The full computed layout: where each card goes, the connectors, and the overall canvas size.
data class BracketLayout(
    val positions: Map<String, BracketPos>,
    val connectors: List<BracketConnector>,
    val width: Float,
    val height: Float
)

// Sizing inputs for the layout math (kept as plain numbers so this is pure and unit-testable).
data class BracketMetrics(
    val cardWidth: Float,
    val cardHeight: Float,
    val hGap: Float,   // horizontal gap between rounds
    val vGap: Float    // vertical gap between round-1 cards
)

// Pure layout math mapping the match list to x-by-round, y-by-slot, with each later match centered between its two feeders.
fun computeBracketLayout(matches: List<Match>, metrics: BracketMetrics): BracketLayout {
    if (matches.isEmpty()) return BracketLayout(emptyMap(), emptyList(), 0f, 0f)

    val byId = matches.associateBy { it.id }
    val rounds = matches.map { it.round }.distinct().sorted()

    // Column x for a round (rounds are 1-based and contiguous in a single-elimination bracket).
    fun xForRound(round: Int): Float = (round - rounds.first()) * (metrics.cardWidth + metrics.hGap)

    val positions = HashMap<String, BracketPos>()

    // Round 1 (the leaves): stack by slot order so the whole tree has a stable vertical anchor.
    val firstRound = matches.filter { it.round == rounds.first() }.sortedBy { it.slot }
    firstRound.forEachIndexed { index, m ->
        positions[m.id] = BracketPos(m.id, xForRound(m.round), index * (metrics.cardHeight + metrics.vGap))
    }

    // Later rounds: a parent's y is the mean of its already-placed children's y (parent-centering).
    for (round in rounds.drop(1)) {
        val inRound = matches.filter { it.round == round }.sortedBy { it.slot }
        for (m in inRound) {
            val childYs = matches
                .filter { it.nextMatchId == m.id }
                .mapNotNull { positions[it.id]?.y }
            // Fall back to slot anchoring if (unexpectedly) no child is placed yet.
            val y = if (childYs.isNotEmpty()) childYs.average().toFloat()
            else m.slot * (metrics.cardHeight + metrics.vGap)
            positions[m.id] = BracketPos(m.id, xForRound(round), y)
        }
    }

    // One connector per match that advances somewhere (winner feeds nextMatchId).
    val connectors = matches
        .filter { it.nextMatchId != null && byId.containsKey(it.nextMatchId) }
        .map { BracketConnector(it.id, it.nextMatchId!!) }

    val width = (rounds.size * metrics.cardWidth) + ((rounds.size - 1) * metrics.hGap)
    val height = (positions.values.maxOfOrNull { it.y } ?: 0f) + metrics.cardHeight
    return BracketLayout(positions, connectors, width, height)
}
