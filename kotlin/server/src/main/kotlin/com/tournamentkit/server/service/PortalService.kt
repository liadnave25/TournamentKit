package com.tournamentkit.server.service

import com.tournamentkit.server.auth.ApiKey
import com.tournamentkit.server.data.AuditRepository
import com.tournamentkit.server.data.CountersRepository
import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.data.SdkLogRepository
import com.tournamentkit.server.data.TournamentRepository
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.server.routes.AnalyticsView
import com.tournamentkit.server.routes.CreateProjectResponse
import com.tournamentkit.server.data.ProjectSummary
import com.tournamentkit.server.routes.RotateKeyResponse
import com.tournamentkit.server.routes.TournamentSummary
import com.tournamentkit.shared.Template
import java.util.UUID
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import com.tournamentkit.shared.TournamentStatus

// Orchestrates the portal (management) operations: templates, tournament admin, audit, keys, analytics.
// Ownership is verified by the route layer before any of these run.
class PortalService(
    private val projects: ProjectRepository,
    private val tournaments: TournamentRepository,
    private val audit: AuditRepository,
    private val reports: ReportService,
    private val tournamentService: TournamentService,
    private val counters: CountersRepository? = null,
    // Optional (defaulted) so existing tests that don't exercise SDK logs can omit it.
    private val sdkLogRepo: SdkLogRepository? = null
) {

    // ---------- Projects ----------

    // Lists the projects owned by a developer (for the portal's project switcher).
    fun listProjects(ownerUid: String): List<ProjectSummary> =
        projects.listByOwner(ownerUid).sortedByDescending { it.createdAt }

    // Creates a new project owned by the caller and returns its first API key ONCE (only the hash is stored).
    fun createProject(ownerUid: String, name: String): CreateProjectResponse {
        require(name.isNotBlank()) { "project name is required" }
        // A short, readable, collision-checked project id.
        var id = newProjectId()
        while (projects.exists(id)) id = newProjectId()

        val apiKey = PortalKey.generate()
        projects.upsertProject(id, name = name.trim(), apiKeyHash = ApiKey.sha256(apiKey), ownerUid = ownerUid)
        return CreateProjectResponse(id = id, name = name.trim(), apiKey = apiKey)
    }

    // ---------- Templates ----------

    // Lists all templates in the project.
    fun listTemplates(projectId: String): List<Template> = projects.listTemplates(projectId)

    // Validates and stores a new template.
    fun createTemplate(projectId: String, template: Template): Template {
        validateTemplate(template)
        projects.putTemplate(projectId, template)
        return template
    }

    // Validates and overwrites an existing template.
    // NOTE: editing a template never affects running tournaments — their rules are snapshotted (spec §6).
    fun updateTemplate(projectId: String, templateId: String, template: Template): Template {
        val toSave = template.copy(id = templateId)
        validateTemplate(toSave)
        projects.putTemplate(projectId, toSave)
        return toSave
    }

    // Deletes a template, refusing if a non-FINISHED tournament still references it.
    fun deleteTemplate(projectId: String, templateId: String) {
        val blocking = tournaments.listByProject(projectId).any {
            it.templateId == templateId && it.status != TournamentStatus.FINISHED
        }
        // Finished tournaments may keep pointing at a deleted template (their rules are snapshotted).
        if (blocking) {
            throw TKException(TKErrorCode.TK_TOURNAMENT_LOCKED, "template is in use by an active tournament")
        }
        projects.deleteTemplate(projectId, templateId)
    }

    // ---------- Tournaments management ----------

    // Lists tournaments newest-first, optionally filtered by status, as lightweight summary rows.
    fun listTournaments(projectId: String, status: String?): List<TournamentSummary> {
        val wanted = status?.let { TournamentStatus.valueOf(it) }
        return tournaments.listByProject(projectId)
            .filter { wanted == null || it.status == wanted }
            .sortedByDescending { it.createdAt }
            .map { TournamentSummary(it.id, it.name, it.status.name, it.participants.size, it.createdAt) }
    }

    // Full view of one tournament, confirming it belongs to this project first.
    fun tournamentView(projectId: String, tournamentId: String): com.tournamentkit.server.routes.TournamentView {
        ownedTournament(projectId, tournamentId)
        return tournamentService.view(tournamentId)
    }

    // Freezes an ACTIVE tournament (pauses player reporting) and logs it.
    fun freeze(projectId: String, tournamentId: String, adminUid: String): Tournament {
        val t = ownedTournament(projectId, tournamentId)
        if (t.status != TournamentStatus.ACTIVE) {
            throw TKException(TKErrorCode.TK_TOURNAMENT_LOCKED, "only ACTIVE tournaments can be frozen")
        }
        val frozen = t.copy(status = TournamentStatus.FROZEN)
        tournaments.put(frozen)
        audit.append(tournamentId, logEntry("FREEZE", adminUid))
        counters?.onStatusChanged(projectId, TournamentStatus.ACTIVE, TournamentStatus.FROZEN)
        return frozen
    }

    // Unfreezes a FROZEN tournament (back to ACTIVE) and logs it.
    fun unfreeze(projectId: String, tournamentId: String, adminUid: String): Tournament {
        val t = ownedTournament(projectId, tournamentId)
        if (t.status != TournamentStatus.FROZEN) {
            throw TKException(TKErrorCode.TK_TOURNAMENT_LOCKED, "only FROZEN tournaments can be unfrozen")
        }
        val active = t.copy(status = TournamentStatus.ACTIVE)
        tournaments.put(active)
        audit.append(tournamentId, logEntry("UNFREEZE", adminUid))
        counters?.onStatusChanged(projectId, TournamentStatus.FROZEN, TournamentStatus.ACTIVE)
        return active
    }

    // Hard-deletes a tournament and all its data, logging DELETE_TOURNAMENT to the project audit log first (the tournament's own log is destroyed with it).
    fun deleteTournament(projectId: String, tournamentId: String, adminUid: String) {
        val t = ownedTournament(projectId, tournamentId)   // 404 if missing or not in this project
        // Count confirmed matches before deleting so the counters doc stays accurate.
        val confirmedCount = tournaments.getMatches(tournamentId).count { it.status == com.tournamentkit.shared.MatchStatus.CONFIRMED }
        audit.appendProject(projectId, mapOf(
            "action" to "DELETE_TOURNAMENT",
            "tournamentId" to tournamentId,
            "adminUid" to adminUid,
            "timestamp" to System.currentTimeMillis()
        ))
        tournaments.delete(tournamentId)
        counters?.onTournamentDeleted(projectId, t.status, confirmedCount)
    }

    // Admin-overrides a match result; ReportService runs the transaction and writes the audit entry.
    fun overrideResult(projectId: String, tournamentId: String, matchId: String, adminUid: String, score: TKScore, reason: String) {
        ownedTournament(projectId, tournamentId)   // ensure it exists in this project
        reports.override(projectId, tournamentId, matchId, adminUid, score, reason)
    }

    // Returns the audit log for a tournament, newest first.
    fun auditLog(projectId: String, tournamentId: String): List<Map<String, Any?>> {
        ownedTournament(projectId, tournamentId)
        return audit.list(tournamentId)
    }

    // Returns the SDK-call log for a tournament, newest first.
    fun sdkLogs(projectId: String, tournamentId: String): List<Map<String, Any?>> {
        ownedTournament(projectId, tournamentId)
        return sdkLogRepo?.list(tournamentId) ?: emptyList()
    }

    // ---------- API keys ----------

    // Generates a new API key, stores ONLY its hash, and returns the plaintext ONCE (never retrievable again).
    fun rotateKey(projectId: String, adminUid: String): RotateKeyResponse {
        val newKey = PortalKey.generate()
        projects.setApiKeyHash(projectId, ApiKey.sha256(newKey))
        // Audit the rotation at the project level — never log the key or its hash.
        audit.appendProject(projectId, mapOf(
            "action" to "KEY_ROTATED",
            "adminUid" to adminUid,
            "timestamp" to System.currentTimeMillis()
        ))
        return RotateKeyResponse(apiKey = newKey)
    }

    // ---------- Analytics ----------

    // Returns dashboard numbers from the project-level counters doc (O(1) reads) with a fallback
    // to a full scan when the counters doc does not exist yet (e.g. first call on an old project).
    fun analytics(projectId: String): AnalyticsView {
        val snap = counters?.get(projectId)
        // Still need the tournament list to compute distinct participants (can't do this incrementally).
        val all = tournaments.listByProject(projectId)
        val distinctUsers = all.flatMap { it.participants.map { p -> p.userId } }.toSet().size
        return if (snap != null) {
            AnalyticsView(
                tournamentsTotal = snap.tournamentsTotal,
                tournamentsByStatus = snap.tournamentsByStatus,
                participantsTotal = distinctUsers,
                matchesConfirmed = snap.confirmedMatchesTotal,
                lastTournamentCreatedAt = snap.lastTournamentCreatedAt
            )
        } else {
            // Counters doc missing: fall back to full scan (only happens before first tournament is created).
            val byStatus = all.groupingBy { it.status.name }.eachCount()
            val confirmedMatches = all.sumOf { t ->
                tournaments.getMatches(t.id).count { it.status == com.tournamentkit.shared.MatchStatus.CONFIRMED }
            }
            AnalyticsView(
                tournamentsTotal = all.size,
                tournamentsByStatus = byStatus,
                participantsTotal = distinctUsers,
                matchesConfirmed = confirmedMatches,
                lastTournamentCreatedAt = all.maxOfOrNull { it.createdAt }
            )
        }
    }

    // ---------- helpers ----------

    // Loads a tournament and confirms it belongs to this project (else 404).
    private fun ownedTournament(projectId: String, tournamentId: String): Tournament {
        val t = tournaments.get(tournamentId)
            ?: throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "tournament $tournamentId not found")
        if (t.projectId != projectId) {
            throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "tournament not in this project")
        }
        return t
    }

    // Builds a simple audit-log entry for freeze/unfreeze actions.
    private fun logEntry(action: String, adminUid: String): Map<String, Any?> =
        mapOf("action" to action, "adminUid" to adminUid, "timestamp" to System.currentTimeMillis())
}

// A short, readable, unique-enough project id, e.g. "proj-1a2b3c4d".
fun newProjectId(): String = "proj-${UUID.randomUUID().toString().take(8)}"
