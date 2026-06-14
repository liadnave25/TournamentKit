package com.tournamentkit.server.data

import com.google.cloud.firestore.Firestore
import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.Tournament
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
    fun projectAuditLog(projectId: String) = "projects/$projectId/auditLog"
}

// Reads/writes the project doc (used by auth and dev seeding).
class ProjectRepository(private val db: Firestore) {

    // Returns the stored apiKeyHash for a project, or null if the project does not exist.
    fun apiKeyHash(projectId: String): String? {
        val snap = db.document(Paths.project(projectId)).get().get()
        return if (snap.exists()) snap.getString("apiKeyHash") else null
    }

    // Returns the project's owner uid, or null if the project does not exist / has no owner.
    fun ownerUid(projectId: String): String? {
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
class TournamentRepository(private val db: Firestore) {

    // Writes (or overwrites) a tournament document.
    fun put(t: Tournament) {
        db.document(Paths.tournament(t.id)).set(t.toMap()).get()
    }

    // Loads a tournament by id, or null if missing.
    fun get(tournamentId: String): Tournament? {
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
    fun getMatches(tournamentId: String): List<Match> {
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
    fun getStandings(tournamentId: String): List<Standing> {
        val q = db.collection(Paths.standings(tournamentId)).get().get()
        return q.documents.map { standingFromMap(it.data!!) }
    }

    // Lists every tournament in a project (sorting/filtering is done by the caller).
    fun listByProject(projectId: String): List<Tournament> {
        val q = db.collection("tournaments").whereEqualTo("projectId", projectId).get().get()
        return q.documents.map { tournamentFromMap(it.data!!) }
    }
}

// Appends audit-log entries to a tournament and reads them back.
class AuditRepository(private val db: Firestore) {

    // Appends one audit entry (auto-id) under the tournament's auditLog sub-collection.
    fun append(tournamentId: String, entry: Map<String, Any?>) {
        db.collection(Paths.auditLog(tournamentId)).add(entry).get()
    }

    // Appends one audit entry under the project's auditLog (used for project-level actions like key rotation).
    fun appendProject(projectId: String, entry: Map<String, Any?>) {
        db.collection(Paths.projectAuditLog(projectId)).add(entry).get()
    }

    // Returns all audit entries for a tournament, newest first.
    fun list(tournamentId: String): List<Map<String, Any?>> {
        val q = db.collection(Paths.auditLog(tournamentId)).get().get()
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
