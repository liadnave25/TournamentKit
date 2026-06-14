package com.tournamentkit.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Transaction
import com.tournamentkit.server.data.Paths
import com.tournamentkit.server.data.matchFromMap
import com.tournamentkit.server.data.toMap
import com.tournamentkit.server.data.tournamentFromMap
import com.tournamentkit.server.engine.EloCalculator
import com.tournamentkit.server.engine.GroupsDraw
import com.tournamentkit.server.engine.Group
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.server.engine.buildKnockoutFromGroups
import com.tournamentkit.server.engine.decideWinner
import com.tournamentkit.server.engine.progressKnockout
import com.tournamentkit.server.engine.recalcStandings
import com.tournamentkit.server.engine.updateRatings
import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.TournamentStatus
import com.tournamentkit.server.engine.Outcome as EngineOutcome

// Handles reporting, confirming, and admin-overriding results — the only place that mutates
// match/standing/rating state. Every path runs through one Firestore transaction.
class ReportService(private val db: Firestore) {

    // Reports a score. If the template requires confirmation, this only marks the match REPORTED;
    // otherwise it immediately runs the full progression transaction.
    fun report(projectId: String, tournamentId: String, matchId: String, userId: String, score: TKScore) {
        runResultTransaction(projectId, tournamentId, matchId, userId, score, confirming = false)
    }

    // Confirms a previously REPORTED result (the OTHER player). Runs the full progression transaction.
    fun confirm(projectId: String, tournamentId: String, matchId: String, userId: String) {
        runResultTransaction(projectId, tournamentId, matchId, userId, score = null, confirming = true)
    }

    // Admin override (portal): apply a result regardless of who reports it, with an audit reason.
    // Reuses the same progression transaction; bypasses the player-identity and FROZEN checks.
    fun override(projectId: String, tournamentId: String, matchId: String, adminUid: String, score: TKScore, reason: String) {
        require(reason.isNotBlank()) { "override reason is required" }
        runResultTransaction(
            projectId, tournamentId, matchId,
            userId = adminUid, score = score, confirming = false,
            adminOverride = true, adminReason = reason
        )
    }

    // The single atomic operation behind report/confirm/override. EVERYTHING below happens in ONE transaction.
    // Firestore rule: ALL reads must come before ANY writes — the code is split into a read phase then a write phase.
    private fun runResultTransaction(
        projectId: String,
        tournamentId: String,
        matchId: String,
        userId: String,
        score: TKScore?,
        confirming: Boolean,
        adminOverride: Boolean = false,
        adminReason: String? = null
    ) = unwrapping {
        db.runTransaction<Unit> { tx ->
            // ---------- READ PHASE (no writes allowed yet) ----------

            // 1. Read the match.
            val matchRef = db.document(Paths.match(tournamentId, matchId))
            val matchSnap = tx.get(matchRef).get()
            if (!matchSnap.exists()) throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "match not found")
            val match = matchFromMap(matchSnap.data!!)

            // 2. Players-only for report/confirm; admin override bypasses this check.
            if (!adminOverride && userId != match.homeId && userId != match.awayId) {
                throw TKException(TKErrorCode.TK_NOT_PARTICIPANT, "user $userId is not in this match")
            }

            // 3. Read the tournament — we use its rules SNAPSHOT, never the template (spec §6).
            val tournRef = db.document(Paths.tournament(tournamentId))
            val tournSnap = tx.get(tournRef).get()
            if (!tournSnap.exists()) throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "tournament not found")
            val tournament = tournamentFromMap(tournSnap.data!!)
            val rules = tournament.rules

            // 3a. FROZEN tournaments reject player report/confirm (portal pause). Admin override still works.
            if (!adminOverride && tournament.status == TournamentStatus.FROZEN) {
                throw TKException(TKErrorCode.TK_TOURNAMENT_FROZEN, "tournament is frozen")
            }

            // 4. Status checks differ for the admin re-override of an already-CONFIRMED match.
            val previousScore = match.score
            if (match.status == MatchStatus.CONFIRMED) {
                if (!adminOverride) {
                    throw TKException(TKErrorCode.TK_MATCH_ALREADY_REPORTED, "match $matchId already confirmed")
                }
                // Re-overriding a confirmed match is only safe if its consequences have not propagated.
                val downstream = match.nextMatchId?.let { matchFromMap(tx.get(db.document(Paths.match(tournamentId, it))).get().data!!) }
                if (!overrideAllowed(match.status, rules.type, match.nextMatchId != null, downstream?.status)) {
                    throw TKException(
                        TKErrorCode.TK_INVALID_SCORE,
                        "cannot override: the downstream match has already been played (no cascading rollback)"
                    )
                }
            }

            // ----- Confirmation flow (report/confirm only) -----

            // Case A: confirmation required, this is the first report -> just mark REPORTED, no progression.
            if (!adminOverride && rules.requireConfirmation && !confirming) {
                val reported = match.copy(score = score, status = MatchStatus.REPORTED)
                tx.set(matchRef, reported.toMap())
                return@runTransaction
            }
            // Case B: confirm() called but the template does not require confirmation -> invalid.
            if (!adminOverride && !rules.requireConfirmation && confirming) {
                throw TKException(TKErrorCode.TK_INVALID_SCORE, "this template does not require confirmation")
            }
            // Case C: confirming -> a score must already exist on the match.
            val effectiveScore: TKScore = when {
                confirming -> {
                    if (match.status != MatchStatus.REPORTED || match.score == null) {
                        throw TKException(TKErrorCode.TK_INVALID_SCORE, "match is not awaiting confirmation")
                    }
                    match.score!!
                }
                else -> score ?: throw TKException(TKErrorCode.TK_INVALID_SCORE, "score is required")
            }

            // Validate the score against the rules (throws TK_INVALID_SCORE on a knockout draw, negatives, etc.).
            val decision = decideWinner(match.copy(status = MatchStatus.PENDING), effectiveScore, rules)

            // For league/groups we also need ALL matches (to recompute the table) — still in the read phase.
            val allMatches: List<Match> =
                if (rules.type == TemplateType.KNOCKOUT) emptyList()
                else {
                    val q = tx.get(db.collection(Paths.matches(tournamentId))).get()
                    q.documents.map { matchFromMap(it.data!!) }
                }

            // Read both players' ratings now (read phase) so we can write new ELO after the match.
            val homeRatingRef = db.document(Paths.rating(projectId, match.homeId))
            val awayRef = match.awayId
            val awayRatingRef = awayRef?.let { db.document(Paths.rating(projectId, it)) }
            val homeRating = tx.get(homeRatingRef).get().let { if (it.exists()) (it.get("rating") as Number).toInt() else EloCalculator.DEFAULT_RATING }
            val awayRating = awayRatingRef?.let { tx.get(it).get() }
                ?.let { if (it.exists()) (it.get("rating") as Number).toInt() else EloCalculator.DEFAULT_RATING }
                ?: EloCalculator.DEFAULT_RATING

            // ---------- WRITE PHASE (all reads are done) ----------

            val confirmedMatch = match.copy(score = effectiveScore, status = MatchStatus.CONFIRMED)
            tx.set(matchRef, confirmedMatch.toMap())

            var tournamentFinished = false

            if (rules.type == TemplateType.KNOCKOUT) {
                // Knockout: advance the winner into the next match (one side), or finish the tournament.
                // progressKnockout treats the match as a fresh decisive game (status PENDING) so a re-override works too.
                val result = progressKnockout(match.copy(status = MatchStatus.PENDING), effectiveScore, allMatches)
                result.advancement?.let { adv ->
                    val targetRef = db.document(Paths.match(tournamentId, adv.targetMatchId))
                    val sideField = if (adv.asHome) "homeId" else "awayId"
                    tx.update(targetRef, sideField, adv.userId)
                }
                tournamentFinished = result.tournamentFinished
            } else {
                // League/groups: recompute the whole table from confirmed matches (this one included).
                val confirmedSoFar = allMatches.map { if (it.id == match.id) confirmedMatch else it }
                val newStandings = recalcStandings(tournament.participants, confirmedSoFar, rules)
                for (s in newStandings) {
                    tx.set(db.document(Paths.standing(tournamentId, s.userId)), s.toMap())
                }

                if (rules.type == TemplateType.LEAGUE) {
                    // League ends when every scheduled match is confirmed.
                    tournamentFinished = confirmedSoFar.all { it.status == MatchStatus.CONFIRMED }
                } else {
                    // Groups: when every group match is confirmed, build and write the knockout stage.
                    val groupsDone = confirmedSoFar.all { it.status == MatchStatus.CONFIRMED }
                    if (groupsDone) {
                        writeKnockoutFromGroups(tx, tournamentId, tournament.participants, confirmedSoFar, rules, newStandings)
                        // Mark that the knockout stage has begun; the tournament is not finished yet.
                        tx.update(tournRef, "knockoutStarted", true)
                    }
                }
            }

            // ELO updates after every confirmed, decisive match (spec §5: rating is cumulative per game).
            if (decision.outcome != EngineOutcome.DRAW && awayRef != null) {
                val winnerIsHome = decision.outcome == EngineOutcome.HOME_WIN
                val (wRating, lRating) = if (winnerIsHome) homeRating to awayRating else awayRating to homeRating
                val (newW, newL) = updateRatings(wRating, lRating, isDraw = false)
                val winnerId = if (winnerIsHome) match.homeId else awayRef
                val loserId = if (winnerIsHome) awayRef else match.homeId
                tx.set(db.document(Paths.rating(projectId, winnerId)), mapOf("rating" to newW, "updatedAt" to System.currentTimeMillis()))
                tx.set(db.document(Paths.rating(projectId, loserId)), mapOf("rating" to newL, "updatedAt" to System.currentTimeMillis()))
            } else if (decision.outcome == EngineOutcome.DRAW && awayRef != null) {
                // A draw still nudges both ratings toward each other.
                val (newHome, newAway) = updateRatings(homeRating, awayRating, isDraw = true)
                tx.set(homeRatingRef, mapOf("rating" to newHome, "updatedAt" to System.currentTimeMillis()))
                tx.set(awayRatingRef!!, mapOf("rating" to newAway, "updatedAt" to System.currentTimeMillis()))
            }

            // Mark the tournament finished if this result ended it.
            if (tournamentFinished) {
                tx.update(tournRef, "status", TournamentStatus.FINISHED.name)
            }

            // Admin overrides are written to the audit log in the same transaction.
            if (adminOverride) {
                val entry = mutableMapOf<String, Any?>(
                    "action" to "OVERRIDE_RESULT",
                    "matchId" to matchId,
                    "newScore" to effectiveScore.toMap(),
                    "reason" to adminReason,
                    "adminUid" to userId,
                    "timestamp" to System.currentTimeMillis()
                )
                previousScore?.let { entry["oldScore"] = it.toMap() }
                tx.set(db.collection(Paths.auditLog(tournamentId)).document(), entry)
            }
            Unit
        }.get()
    }

    // Builds the post-group knockout bracket and writes its matches (called inside the transaction).
    private fun writeKnockoutFromGroups(
        tx: Transaction,
        tournamentId: String,
        participants: List<com.tournamentkit.shared.Participant>,
        confirmedMatches: List<Match>,
        rules: com.tournamentkit.shared.Template,
        @Suppress("UNUSED_PARAMETER") flatStandings: List<com.tournamentkit.shared.Standing>
    ) {
        // Rebuild the group structure from the confirmed matches (group membership = who played whom).
        val config = groupConfigFor(participants.size)
        // Reconstruct groups by re-running the deterministic group assignment is not possible without the
        // original Random; instead we infer groups from the matches: players who share matches form a group.
        val groups = inferGroups(participants, confirmedMatches)
        val perGroupStandings = groups.associate { g ->
            g.index.toString() to recalcStandings(g.participants, g.matches, rules)
        }
        val knockout = buildKnockoutFromGroups(GroupsDraw(groups), perGroupStandings, config.qualifiersPerGroup)
        for (m in knockout) {
            tx.set(db.document(Paths.match(tournamentId, "ko-${m.id}")), m.copy(id = "ko-${m.id}").toMap())
        }
    }

    // Groups players by their shared matches (each connected set of opponents is one group).
    private fun inferGroups(
        participants: List<com.tournamentkit.shared.Participant>,
        matches: List<Match>
    ): List<Group> {
        // Union-find over players connected by a match.
        val parent = HashMap<String, String>()
        participants.forEach { parent[it.userId] = it.userId }
        fun find(x: String): String { var r = x; while (parent[r] != r) r = parent[r]!!; return r }
        fun union(a: String, b: String) { parent[find(a)] = find(b) }
        for (m in matches) m.awayId?.let { union(m.homeId, it) }

        val byRoot = participants.groupBy { find(it.userId) }
        return byRoot.values.mapIndexed { idx, members ->
            val ids = members.map { it.userId }.toSet()
            val groupMatches = matches.filter { it.homeId in ids && (it.awayId == null || it.awayId in ids) }
            Group(index = idx, participants = members, matches = groupMatches)
        }
    }

    // Firestore wraps exceptions thrown inside a transaction; rethrow the original TKException so
    // StatusPages maps it to the right HTTP status instead of a generic 500.
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
