package com.tournamentkit.server.data

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Transaction
import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.Tournament
import com.tournamentkit.shared.TournamentStatus
import kotlinx.serialization.Serializable

// A project the signed-in developer owns (for the portal's project list/switcher).
@Serializable
data class ProjectSummary(val id: String, val name: String, val createdAt: Long)

// Firestore document/collection paths in one place, matching the layout in spec §6.
object Paths {
    fun project(projectId: String) = "projects/$projectId"
    fun template(projectId: String, templateId: String) = "projects/$projectId/templates/$templateId"
    fun rating(projectId: String, userId: String) = "projects/$projectId/ratings/$userId"
    fun tournament(tournamentId: String) = "tournaments/$tournamentId"
    fun matches(tournamentId: String) = "tournaments/$tournamentId/matches"
    fun match(tournamentId: String, matchId: String) = "tournaments/$tournamentId/matches/$matchId"
    fun standings(tournamentId: String) = "tournaments/$tournamentId/standings"
    fun standing(tournamentId: String, userId: String) = "tournaments/$tournamentId/standings/$userId"
    fun templates(projectId: String) = "projects/$projectId/templates"
    fun auditLog(tournamentId: String) = "tournaments/$tournamentId/auditLog"
    fun sdkLogs(tournamentId: String) = "tournaments/$tournamentId/sdkLogs"
    fun projectAuditLog(projectId: String) = "projects/$projectId/auditLog"
    // Single document that tracks aggregate counts so analytics() avoids per-tournament reads.
    fun counters(projectId: String) = "projects/$projectId/counters/stats"
}

// Reads/writes the project doc (used by auth and dev seeding).
// `open` so tests can substitute an in-memory fake for ownership checks.
open class ProjectRepository(private val db: Firestore) {

    // Returns the stored apiKeyHash for a project, or null if the project does not exist.
    fun apiKeyHash(projectId: String): String? {
        val snap = db.document(Paths.project(projectId)).get().get()
        return if (snap.exists()) snap.getString("apiKeyHash") else null
    }

    // Returns the project's owner uid, or null if the project does not exist / has no owner.
    open fun ownerUid(projectId: String): String? {
        val snap = db.document(Paths.project(projectId)).get().get()
        return if (snap.exists()) snap.getString("ownerUid") else null
    }

    // Returns true if a project document already exists (used to avoid id collisions on create).
    fun exists(projectId: String): Boolean =
        db.document(Paths.project(projectId)).get().get().exists()

    // Lists the (id, name, createdAt) of every project owned by a given Firebase uid, for the portal.
    fun listByOwner(ownerUid: String): List<ProjectSummary> {
        val q = db.collection("projects").whereEqualTo("ownerUid", ownerUid).get().get()
        return q.documents.map {
            ProjectSummary(
                id = it.id,
                name = it.getString("name") ?: it.id,
                createdAt = (it.get("createdAt") as? Number)?.toLong() ?: 0L
            )
        }
    }

    // Creates (or overwrites) a project document with its api-key hash and optional owner.
    fun upsertProject(projectId: String, name: String, apiKeyHash: String, ownerUid: String? = null) {
        db.document(Paths.project(projectId)).set(
            mapOf(
                "name" to name,
                "apiKeyHash" to apiKeyHash,
                "ownerUid" to ownerUid,
                "createdAt" to System.currentTimeMillis()
            )
        ).get()
    }

    // Replaces only the project's api-key hash (used by key rotation; leaves owner/name intact).
    fun setApiKeyHash(projectId: String, apiKeyHash: String) {
        db.document(Paths.project(projectId)).update("apiKeyHash", apiKeyHash).get()
    }

    // Stores a template document under the project.
    fun putTemplate(projectId: String, template: Template) {
        db.document(Paths.template(projectId, template.id)).set(template.toMap()).get()
    }

    // Loads a template, or null if missing.
    fun getTemplate(projectId: String, templateId: String): Template? {
        val snap = db.document(Paths.template(projectId, templateId)).get().get()
        return if (snap.exists()) templateFromMap(snap.data!!) else null
    }

    // Lists every template defined in the project.
    fun listTemplates(projectId: String): List<Template> {
        val q = db.collection(Paths.templates(projectId)).get().get()
        return q.documents.map { templateFromMap(it.data!!) }
    }

    // Deletes a template document.
    fun deleteTemplate(projectId: String, templateId: String) {
        db.document(Paths.template(projectId, templateId)).delete().get()
    }
}

// Reads/writes tournament documents and their match/standing sub-collections.
// `open` so tests can substitute an in-memory fake for the delete orchestration.
open class TournamentRepository(private val db: Firestore) {

    // Writes (or overwrites) a tournament document.
    fun put(t: Tournament) {
        db.document(Paths.tournament(t.id)).set(t.toMap()).get()
    }

    // Loads a tournament by id, or null if missing.
    open fun get(tournamentId: String): Tournament? {
        val snap = db.document(Paths.tournament(tournamentId)).get().get()
        return if (snap.exists()) tournamentFromMap(snap.data!!) else null
    }

    // Finds a tournament by its join code within a project (join codes are unique per project in practice).
    fun findByJoinCode(projectId: String, joinCode: String): Tournament? {
        val q = db.collection("tournaments")
            .whereEqualTo("projectId", projectId)
            .whereEqualTo("joinCode", joinCode)
            .limit(1)
            .get().get()
        val doc = q.documents.firstOrNull() ?: return null
        return tournamentFromMap(doc.data!!)
    }

    // Writes a list of matches in a single batched write (used when a tournament starts).
    fun writeMatches(tournamentId: String, matches: List<Match>) {
        val batch = db.batch()
        for (m in matches) {
            batch.set(db.document(Paths.match(tournamentId, m.id)), m.toMap())
        }
        batch.commit().get()
    }

    // Loads all matches of a tournament, ordered by round then slot.
    open fun getMatches(tournamentId: String): List<Match> {
        val q = db.collection(Paths.matches(tournamentId)).get().get()
        return q.documents.map { matchFromMap(it.data!!) }
            .sortedWith(compareBy({ it.round }, { it.slot }))
    }

    // Writes a list of standing rows in a single batched write (used when a league/groups starts).
    fun writeStandings(tournamentId: String, standings: List<Standing>) {
        val batch = db.batch()
        for (s in standings) {
            batch.set(db.document(Paths.standing(tournamentId, s.userId)), s.toMap())
        }
        batch.commit().get()
    }

    // Loads all standing rows of a tournament (unsorted; the engine defines the order).
    open fun getStandings(tournamentId: String): List<Standing> {
        val q = db.collection(Paths.standings(tournamentId)).get().get()
        return q.documents.map { standingFromMap(it.data!!) }
    }

    // Lists every tournament in a project (sorting/filtering is done by the caller).
    fun listByProject(projectId: String): List<Tournament> {
        val q = db.collection("tournaments").whereEqualTo("projectId", projectId).get().get()
        return q.documents.map { tournamentFromMap(it.data!!) }
    }

    // Hard-deletes a tournament and its sub-collection docs (matches, standings, auditLog) in one batched write so no orphan survives.
    open fun delete(tournamentId: String) {
        val batch = db.batch()
        for (sub in listOf(Paths.matches(tournamentId), Paths.standings(tournamentId), Paths.auditLog(tournamentId), Paths.sdkLogs(tournamentId))) {
            for (doc in db.collection(sub).get().get().documents) batch.delete(doc.reference)
        }
        batch.delete(db.document(Paths.tournament(tournamentId)))
        batch.commit().get()
    }
}

// Appends audit-log entries to a tournament and reads them back.
// `open` so tests can substitute an in-memory fake for the project-level delete audit.
open class AuditRepository(private val db: Firestore) {

    // Appends one audit entry (auto-id) under the tournament's auditLog sub-collection.
    fun append(tournamentId: String, entry: Map<String, Any?>) {
        db.collection(Paths.auditLog(tournamentId)).add(entry).get()
    }

    // Appends one audit entry under the project's auditLog (used for project-level actions like key rotation).
    open fun appendProject(projectId: String, entry: Map<String, Any?>) {
        db.collection(Paths.projectAuditLog(projectId)).add(entry).get()
    }

    // Returns all audit entries for a tournament, newest first.
    fun list(tournamentId: String): List<Map<String, Any?>> {
        val q = db.collection(Paths.auditLog(tournamentId)).get().get()
        return q.documents.map { it.data }
            .sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
    }
}

// Appends per-tournament SDK (/v1) call-log entries and reads them back (mirrors AuditRepository).
// `open` so tests can substitute an in-memory fake that captures appended entries.
open class SdkLogRepository(private val db: Firestore) {

    // Appends one SDK-call log entry (auto-id) under the tournament's sdkLogs sub-collection.
    open fun append(tournamentId: String, entry: Map<String, Any?>) {
        db.collection(Paths.sdkLogs(tournamentId)).add(entry).get()
    }

    // Returns all SDK-call log entries for a tournament, newest first.
    fun list(tournamentId: String): List<Map<String, Any?>> {
        val q = db.collection(Paths.sdkLogs(tournamentId)).get().get()
        return q.documents.map { it.data }
            .sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
    }
}

// Reads/writes per-project cumulative ELO ratings.
class RatingRepository(private val db: Firestore) {

    // Returns a user's rating, or the supplied default if they have none yet.
    fun get(projectId: String, userId: String, default: Int): Int {
        val snap = db.document(Paths.rating(projectId, userId)).get().get()
        return if (snap.exists()) (snap.get("rating") as Number).toInt() else default
    }
}

// A snapshot of the projects/{pid}/counters/stats document.
data class CountersSnapshot(
    val tournamentsTotal: Int,
    val tournamentsByStatus: Map<String, Int>,
    val confirmedMatchesTotal: Int,
    val lastTournamentCreatedAt: Long?
)

// Maintains a single counters document per project so analytics() costs 1 read instead of T×2.
// All mutation methods use Firestore's FieldValue.increment() so concurrent writes don't clash.
class CountersRepository(private val db: Firestore) {

    // Returns the current counters snapshot, or null when the document hasn't been created yet.
    fun get(projectId: String): CountersSnapshot? {
        val snap = db.document(Paths.counters(projectId)).get().get()
        if (!snap.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val byStatus = (snap.get("tournamentsByStatus") as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as Number).toInt() } ?: emptyMap()
        return CountersSnapshot(
            tournamentsTotal = (snap.get("tournamentsTotal") as? Number)?.toInt() ?: 0,
            tournamentsByStatus = byStatus,
            confirmedMatchesTotal = (snap.get("confirmedMatchesTotal") as? Number)?.toInt() ?: 0,
            lastTournamentCreatedAt = (snap.get("lastTournamentCreatedAt") as? Number)?.toLong()
        )
    }

    // Increments confirmedMatchesTotal by 1, called inside an existing transaction at report time.
    fun incrementConfirmedInTx(tx: Transaction, projectId: String) {
        val ref = db.document(Paths.counters(projectId))
        tx.update(ref, "confirmedMatchesTotal", com.google.cloud.firestore.FieldValue.increment(1))
    }

    // Moves the count for one status bucket when a tournament's status changes (inside a tx).
    fun changeStatusInTx(tx: Transaction, projectId: String, from: TournamentStatus, to: TournamentStatus) {
        val ref = db.document(Paths.counters(projectId))
        tx.update(ref, "tournamentsByStatus.${from.name}", com.google.cloud.firestore.FieldValue.increment(-1))
        tx.update(ref, "tournamentsByStatus.${to.name}", com.google.cloud.firestore.FieldValue.increment(1))
    }

    // Moves the count for one status bucket outside a transaction (used by start/freeze/unfreeze).
    fun onStatusChanged(projectId: String, from: TournamentStatus, to: TournamentStatus) {
        val ref = db.document(Paths.counters(projectId))
        if (!db.document(Paths.counters(projectId)).get().get().exists()) return
        ref.update(mapOf(
            "tournamentsByStatus.${from.name}" to com.google.cloud.firestore.FieldValue.increment(-1),
            "tournamentsByStatus.${to.name}" to com.google.cloud.firestore.FieldValue.increment(1)
        )).get()
    }

    // Registers a newly created tournament (outside a transaction — creation is not transactional).
    fun onTournamentCreated(projectId: String, status: TournamentStatus, createdAt: Long) {
        val ref = db.document(Paths.counters(projectId))
        db.runTransaction<Unit> { tx ->
            val snap = tx.get(ref).get()
            if (!snap.exists()) {
                // First tournament in this project: initialise the document from scratch.
                tx.set(ref, mapOf(
                    "tournamentsTotal" to 1,
                    "tournamentsByStatus" to mapOf(status.name to 1),
                    "confirmedMatchesTotal" to 0,
                    "lastTournamentCreatedAt" to createdAt
                ))
            } else {
                tx.update(ref, "tournamentsTotal", com.google.cloud.firestore.FieldValue.increment(1))
                tx.update(ref, "tournamentsByStatus.${status.name}", com.google.cloud.firestore.FieldValue.increment(1))
                val last = (snap.get("lastTournamentCreatedAt") as? Number)?.toLong() ?: 0L
                if (createdAt > last) tx.update(ref, "lastTournamentCreatedAt", createdAt)
            }
            Unit
        }.get()
    }

    // Adjusts counters when a tournament is hard-deleted (outside a transaction).
    fun onTournamentDeleted(projectId: String, status: TournamentStatus, confirmedMatchCount: Int) {
        val ref = db.document(Paths.counters(projectId))
        db.document(Paths.counters(projectId)).get().get().let { snap ->
            if (!snap.exists()) return
        }
        val updates = mutableMapOf<String, Any>(
            "tournamentsTotal" to com.google.cloud.firestore.FieldValue.increment(-1),
            "tournamentsByStatus.${status.name}" to com.google.cloud.firestore.FieldValue.increment(-1)
        )
        if (confirmedMatchCount > 0) {
            updates["confirmedMatchesTotal"] = com.google.cloud.firestore.FieldValue.increment(-confirmedMatchCount.toLong())
        }
        ref.update(updates).get()
    }
}
