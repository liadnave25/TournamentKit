package com.tournamentkit.server.routes

import com.tournamentkit.server.auth.portalAuthPlugin
import com.tournamentkit.server.auth.portalUid
import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.server.service.PortalService
import com.tournamentkit.server.service.TournamentService
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TKErrorCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject

// Registers all portal (management) endpoints under /portal/projects/{pid}/...
// Auth is a Firebase ID token; every call additionally checks the caller owns the project.
fun Route.portalRoutes(
    projects: ProjectRepository,
    portal: PortalService,
    tournamentService: TournamentService
) {
    // ---------- Project collection: list owned projects + create a new one ----------
    // These sit at /portal/projects (no {pid} yet): authenticated by token, but no ownership check —
    // create sets ownerUid FROM the verified token, and list filters to that uid.
    route("/portal/projects") {
        install(portalAuthPlugin())

        // List the projects the signed-in developer owns (for the project switcher).
        get {
            call.respond(io { portal.listProjects(call.portalUid) })
        }

        // Create a project owned by the caller; returns the first API key ONCE (only the hash is stored).
        post {
            val body = call.receive<CreateProjectRequest>()
            call.respond(io { portal.createProject(call.portalUid, body.name) })
        }
    }

    route("/portal/projects/{pid}") {
        // Verify the Firebase ID token for every portal request.
        install(portalAuthPlugin())

        // ---------- Templates ----------

        get("/templates") {
            val pid = call.ownedProject(projects)
            call.respond(io { portal.listTemplates(pid) })
        }

        post("/templates") {
            val pid = call.ownedProject(projects)
            val body = call.receive<TemplateRequest>()
            // Generate an id when the client did not supply one.
            val id = body.id.ifBlank { "tmpl-${UUID.randomUUID().toString().take(8)}" }
            val created = io { portal.createTemplate(pid, body.toTemplate(id)) }
            call.respond(created)
        }

        put("/templates/{tid}") {
            val pid = call.ownedProject(projects)
            val tid = call.parameters["tid"]!!
            val body = call.receive<TemplateRequest>()
            val updated = io { portal.updateTemplate(pid, tid, body.toTemplate(tid)) }
            call.respond(updated)
        }

        delete("/templates/{tid}") {
            val pid = call.ownedProject(projects)
            val tid = call.parameters["tid"]!!
            io { portal.deleteTemplate(pid, tid) }
            call.respond(mapOf("deleted" to tid))
        }

        // ---------- Tournaments management ----------

        get("/tournaments") {
            val pid = call.ownedProject(projects)
            val status = call.request.queryParameters["status"]
            call.respond(io { portal.listTournaments(pid, status) })
        }

        get("/tournaments/{tid}") {
            val pid = call.ownedProject(projects)
            val tid = call.parameters["tid"]!!
            // Confirm the tournament is in this project, then reuse the public full-view shape.
            call.respond(io { portal.tournamentView(pid, tid) })
        }

        post("/tournaments/{tid}/freeze") {
            val pid = call.ownedProject(projects)
            val tid = call.parameters["tid"]!!
            call.respond(io { portal.freeze(pid, tid, call.portalUid) })
        }

        post("/tournaments/{tid}/unfreeze") {
            val pid = call.ownedProject(projects)
            val tid = call.parameters["tid"]!!
            call.respond(io { portal.unfreeze(pid, tid, call.portalUid) })
        }

        post("/tournaments/{tid}/matches/{mid}/override") {
            val pid = call.ownedProject(projects)
            val tid = call.parameters["tid"]!!
            val mid = call.parameters["mid"]!!
            val body = call.receive<OverrideRequest>()
            io { portal.overrideResult(pid, tid, mid, call.portalUid, body.score, body.reason) }
            call.respond(io { tournamentService.view(tid) })
        }

        get("/tournaments/{tid}/audit") {
            val pid = call.ownedProject(projects)
            val tid = call.parameters["tid"]!!
            val entries = io { portal.auditLog(pid, tid) }
            // Audit entries are heterogeneous maps; serialize them as raw JSON.
            call.respond(JsonArray(entries.map { it.toJson() }))
        }

        // ---------- API keys ----------

        post("/keys/rotate") {
            val pid = call.ownedProject(projects)
            call.respond(io { portal.rotateKey(pid, call.portalUid) })
        }

        // ---------- Analytics ----------

        get("/analytics") {
            val pid = call.ownedProject(projects)
            call.respond(io { portal.analytics(pid) })
        }
    }
}

// Resolves the {pid} path param and enforces that the authenticated user owns the project (else 403).
private fun ApplicationCall.ownedProject(projects: ProjectRepository): String {
    val pid = parameters["pid"]!!
    val owner = projects.ownerUid(pid)
        ?: throw TKException(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, "project not found")
    if (owner != portalUid) {
        throw TKException(TKErrorCode.TK_FORBIDDEN, "you do not own this project")
    }
    return pid
}

// Builds a Template from the request body with the resolved id.
private fun TemplateRequest.toTemplate(id: String): Template =
    Template(id, type, scoring, maxParticipants, requireConfirmation, reportTimeoutHours)

// Converts an arbitrary Firestore value into a JSON element for audit-log responses.
private fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJson() })
    is List<*> -> JsonArray(map { it.toJson() })
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

// Runs a blocking Firestore call off the Netty event loop.
private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
