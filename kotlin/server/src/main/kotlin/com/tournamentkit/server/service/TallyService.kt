package com.tournamentkit.server.service

import com.google.cloud.firestore.Firestore
import com.tournamentkit.server.data.Paths
import com.tournamentkit.server.data.participantFromMap
import com.tournamentkit.server.data.standingFromMap
import com.tournamentkit.server.data.toMap
import com.tournamentkit.server.data.tournamentFromMap
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.TournamentStatus

// Handles the one TALLY mutation: add points to a person on an open-ended leaderboard.
// Runs in a single Firestore transaction (read tournament + standing, then write participant + standing).
class TallyService(private val db: Firestore) {

    // Adds `points` (may be negative) to a user on a TALLY tournament, auto-creating the
    // participant + standing on their first add. Returns the user's updated Standing.
    fun add(tournamentId: String, userId: String, displayName: String, points: Int): Standing = unwrapping {
        db.runTransaction<Standing> { tx ->
            // ---------- READ PHASE (all reads before any writes) ----------

            // 1. Read the tournament and enforce that this op only applies to a TALLY board.
            val tournRef = db.document(Paths.tournament(tournamentId))
            val tournSnap = tx.get(tournRef).get()
            if (!tournSnap.exists()) throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "tournament not found")
            val tournament = tournamentFromMap(tournSnap.data!!)
            if (tournament.rules.type != TemplateType.TALLY) {
                throw TKException(TKErrorCode.TK_NOT_SUPPORTED_FOR_TYPE, "tally/add only applies to TALLY tournaments")
            }
            // 1a. A frozen board rejects adds, same as report/confirm on other types.
            if (tournament.status == TournamentStatus.FROZEN) {
                throw TKException(TKErrorCode.TK_TOURNAMENT_FROZEN, "tournament is frozen")
            }

            // 2. Read the user's current standing (null = first time we've seen them).
            val standingRef = db.document(Paths.standing(tournamentId, userId))
            val standingSnap = tx.get(standingRef).get()
            val existing = if (standingSnap.exists()) standingFromMap(standingSnap.data!!) else null

            // ---------- WRITE PHASE ----------

            // Auto-join: add the user to the participants list the first time they get points.
            val isNew = existing == null || tournament.participants.none { it.userId == userId }
            if (isNew) {
                val updated = tournament.copy(
                    participants = tournament.participants.filterNot { it.userId == userId } +
                        tallyParticipant(userId, displayName)
                )
                tx.set(tournRef, updated.toMap())
            }

            // Add the points (negative allowed; total may go below zero) and write the standing.
            val merged = mergeTallyPoints(userId, existing, points)
            tx.set(standingRef, merged.toMap())
            merged
        }.get()
    }

    // Firestore wraps exceptions thrown inside a transaction; rethrow the original TKException so
    // StatusPages maps it to the right HTTP status instead of a generic 500. (Same as ReportService.)
    private inline fun <T> unwrapping(block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is TKException) throw cause
                if (cause is IllegalArgumentException) throw cause
                cause = cause.cause
            }
            throw e
        }
    }
}
