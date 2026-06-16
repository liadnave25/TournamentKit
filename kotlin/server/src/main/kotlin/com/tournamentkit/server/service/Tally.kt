package com.tournamentkit.server.service

import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Standing

// Pure functions behind the TALLY leaderboard (no I/O) so they can be unit-tested in isolation.

// Returns the standing row after adding `points` to a user's running total. A null `existing`
// means the user is new, so we start from zero. Points may be negative and the total may go
// below zero — TALLY is a ledger/correction, not a non-negative counter. Only userId+points
// are meaningful for TALLY; the match-derived fields stay 0.
fun mergeTallyPoints(userId: String, existing: Standing?, points: Int): Standing {
    val current = existing?.points ?: 0
    return Standing(
        userId = userId,
        played = 0, won = 0, drawn = 0, lost = 0,
        pointsFor = 0, pointsAgainst = 0,
        points = current + points
    )
}

// Orders a TALLY leaderboard: highest points first, then a stable tiebreak by userId.
// No goal-difference / head-to-head semantics apply to a tally board.
fun tallySort(rows: List<Standing>): List<Standing> =
    rows.sortedWith(compareByDescending<Standing> { it.points }.thenBy { it.userId })

// A participant entry for a user auto-added to a TALLY board on their first points add.
fun tallyParticipant(userId: String, displayName: String): Participant =
    Participant(userId = userId, displayName = displayName)
