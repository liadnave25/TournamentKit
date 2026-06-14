package com.tournamentkit.server.service

import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.data.TournamentRepository
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.server.engine.drawGroups
import com.tournamentkit.server.engine.drawKnockout
import com.tournamentkit.server.engine.drawLeague
import com.tournamentkit.server.routes.TournamentView
import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.Tournament
import com.tournamentkit.shared.TournamentStatus
import java.util.UUID

// Orchestrates tournament lifecycle (create/join/start/read) on top of the repositories and engines.
class TournamentService(
    private val projects: ProjectRepository,
    private val tournaments: TournamentRepository
) {

    // Creates a tournament from a template, snapshots its rules, and auto-joins the creator.
    fun create(projectId: String, templateId: String, name: String, userId: String, displayName: String): Tournament {
        // Load the template the tournament is based on.
        val template = projects.getTemplate(projectId, templateId)
            ?: throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "template $templateId not found")

        val tournament = Tournament(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            templateId = templateId,
            name = name,
            joinCode = JoinCode.generate(),
            status = TournamentStatus.REGISTRATION,
            // The creator is the first participant.
            participants = listOf(Participant(userId, displayName)),
            // Snapshot the rules NOW so later template edits never change a running tournament (spec §6).
            rules = template,
            createdAt = System.currentTimeMillis(),
            startedAt = null
        )
        tournaments.put(tournament)
        return tournament
    }

    // Adds a participant to a tournament found by its join code.
    fun join(projectId: String, joinCode: String, userId: String, displayName: String): Tournament {
        val t = tournaments.findByJoinCode(projectId, joinCode)
            ?: throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "no tournament with code $joinCode")

        // Can only join while registration is open.
        if (t.status != TournamentStatus.REGISTRATION) {
            throw TKException(TKErrorCode.TK_TOURNAMENT_LOCKED, "tournament is not accepting registrations")
        }
        // No double-joining.
        if (t.participants.any { it.userId == userId }) {
            throw TKException(TKErrorCode.TK_ALREADY_JOINED, "$userId already joined")
        }
        // Respect the snapshot's participant cap.
        if (t.participants.size >= t.rules.maxParticipants) {
            throw TKException(TKErrorCode.TK_TOURNAMENT_FULL, "tournament is full")
        }

        val updated = t.copy(participants = t.participants + Participant(userId, displayName))
        tournaments.put(updated)
        return updated
    }

    // Locks registration, runs the matching draw engine, and persists matches + standings.
    fun start(tournamentId: String, actingUserId: String): Tournament {
        val t = tournaments.get(tournamentId)
            ?: throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "tournament $tournamentId not found")

        // Only the creator (first participant) may start the tournament.
        if (t.participants.firstOrNull()?.userId != actingUserId) {
            throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "only the creator may start the tournament")
        }
        if (t.status != TournamentStatus.REGISTRATION) {
            throw TKException(TKErrorCode.TK_TOURNAMENT_LOCKED, "tournament already started")
        }
        if (t.participants.size < 2) {
            throw TKException(TKErrorCode.TK_INVALID_SCORE, "need at least 2 participants to start")
        }

        // Build matches (and initial standings where applicable) per template type.
        val matches: List<Match>
        val standings: List<Standing>
        when (t.rules.type) {
            TemplateType.KNOCKOUT -> {
                matches = drawKnockout(t.participants)
                standings = emptyList()   // knockout has no league table
            }
            TemplateType.LEAGUE -> {
                matches = drawLeague(t.participants)
                standings = blankStandings(t.participants)
            }
            TemplateType.GROUPS_KNOCKOUT -> {
                val config = groupConfigFor(t.participants.size)
                val draw = drawGroups(t.participants, config.groupCount)
                matches = draw.groups.flatMap { it.matches }
                standings = blankStandings(t.participants)
            }
        }

        tournaments.writeMatches(tournamentId, matches)
        if (standings.isNotEmpty()) tournaments.writeStandings(tournamentId, standings)

        val started = t.copy(status = TournamentStatus.ACTIVE, startedAt = System.currentTimeMillis())
        tournaments.put(started)
        return started
    }

    // Returns the full tournament view (tournament + matches + standings).
    fun view(tournamentId: String): TournamentView {
        val t = tournaments.get(tournamentId)
            ?: throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "tournament $tournamentId not found")
        return TournamentView(t, tournaments.getMatches(tournamentId), tournaments.getStandings(tournamentId))
    }

    // Returns just the standings, sorted by the engine's recompute order.
    fun standings(tournamentId: String): List<Standing> {
        val t = tournaments.get(tournamentId)
            ?: throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "tournament $tournamentId not found")
        val confirmed = tournaments.getMatches(tournamentId)
        // Recompute order via the engine so the API always returns a sorted, tie-broken table.
        return com.tournamentkit.server.engine.recalcStandings(t.participants, confirmed, t.rules)
    }

    // A fresh, all-zero standings row for every participant.
    private fun blankStandings(participants: List<Participant>): List<Standing> =
        participants.map { Standing(it.userId, 0, 0, 0, 0, 0, 0, 0) }
}

// Group-stage configuration derived from the participant count (groups of ~4, 2 qualify each).
data class GroupConfig(val groupCount: Int, val qualifiersPerGroup: Int)

// Picks a group count so groups are about 4 players each, with at least 2 groups.
fun groupConfigFor(participantCount: Int): GroupConfig {
    val groupCount = maxOf(2, participantCount / 4)
    return GroupConfig(groupCount = groupCount, qualifiersPerGroup = 2)
}
