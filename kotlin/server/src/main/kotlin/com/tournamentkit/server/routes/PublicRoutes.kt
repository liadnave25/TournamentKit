package com.tournamentkit.server.routes

import com.tournamentkit.server.auth.apiKeyAuthPlugin
import com.tournamentkit.server.auth.projectId
import com.tournamentkit.server.auth.rateLimitPlugin
import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.data.RatingRepository
import com.tournamentkit.server.data.SdkLogRepository
import com.tournamentkit.server.engine.EloCalculator
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.server.service.RateLimiter
import com.tournamentkit.server.service.ReportService
import com.tournamentkit.server.service.TallyService
import com.tournamentkit.server.service.TournamentService
import com.tournamentkit.shared.TKErrorCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Registers all authenticated /v1 endpoints, with the auth plugin running first for every route.
fun Route.publicRoutes(
    projects: ProjectRepository,
    ratings: RatingRepository,
    tournaments: TournamentService,
    reports: ReportService,
    tally: TallyService,
    sdkLogs: SdkLogRepository,
    rateLimiter: RateLimiter
) {
    route("/v1") {
        // Rate-limit per API key (or IP) BEFORE auth, so abusive traffic is throttled either way.
        install(rateLimitPlugin(rateLimiter))
        // Every /v1 request must carry a valid API key + project id.
        install(apiKeyAuthPlugin(projects))

        // Create a tournament from a template (creator auto-joins).
        // Logged on success only — a failed create has no tournament id to attach a log entry to.
        post("/tournaments") {
            val body = call.receive<CreateTournamentRequest>()
            val t = io { tournaments.create(call.projectId, body.templateId, body.name, body.userId, body.displayName) }
            logSdkCall(sdkLogs, t.id, "CREATE", body.userId, null, null, null)
            call.respond(t)
        }

        // Join a tournament by its join code.
        // Logged on success only — a failed join has no tournament id to attach a log entry to.
        post("/tournaments/join") {
            val body = call.receive<JoinRequest>()
            val t = io { tournaments.join(call.projectId, body.joinCode, body.userId, body.displayName) }
            logSdkCall(sdkLogs, t.id, "JOIN", body.userId, null, null, null)
            call.respond(t)
        }

        // Start a tournament (creator only): lock registration and draw the matches.
        post("/tournaments/{id}/start") {
            val id = call.parameters["id"]!!
            val body = call.receive<StartRequest>()
            try {
                val t = io { tournaments.start(id, body.userId) }
                logSdkCall(sdkLogs, id, "START", body.userId, null, null, null)
                call.respond(t)
            } catch (e: Throwable) {
                logSdkCall(sdkLogs, id, "START", body.userId, null, e, emptyMap())
                throw e
            }
        }

        // Report a match result (the heart of the system — runs the progression transaction).
        // Single-writer: the result is final (CONFIRMED) immediately; there is no confirm step.
        post("/matches/report") {
            val body = call.receive<ReportRequest>()
            try {
                // Log AFTER the transaction completes — the log write is never inside it.
                io { reports.report(call.projectId, body.tournamentId, body.matchId, body.userId, body.score) }
                logSdkCall(sdkLogs, body.tournamentId, "REPORT_RESULT", body.userId, body.matchId, null, null)
            } catch (e: Throwable) {
                logSdkCall(sdkLogs, body.tournamentId, "REPORT_RESULT", body.userId, body.matchId, e,
                    mapOf("score" to mapOf("home" to body.score.home, "away" to body.score.away)))
                throw e
            }
            call.respond(io { tournaments.view(body.tournamentId) })
        }

        // Add points to a person on a TALLY leaderboard (auto-joins them on first add).
        post("/tally/add") {
            val body = call.receive<TallyAddRequest>()
            try {
                val standing = io { tally.add(body.tournamentId, body.userId, body.displayName, body.points) }
                logSdkCall(sdkLogs, body.tournamentId, "TALLY_ADD", body.userId, null, null, null)
                call.respond(standing)
            } catch (e: Throwable) {
                logSdkCall(sdkLogs, body.tournamentId, "TALLY_ADD", body.userId, null, e,
                    mapOf("points" to body.points, "displayName" to body.displayName))
                throw e
            }
        }

        // Full tournament view: tournament + matches + standings.
        get("/tournaments/{id}") {
            val id = call.parameters["id"]!!
            try {
                val view = io { tournaments.view(id) }
                logSdkCall(sdkLogs, id, "GET_TOURNAMENT", null, null, null, null)
                call.respond(view)
            } catch (e: Throwable) {
                logSdkCall(sdkLogs, id, "GET_TOURNAMENT", null, null, e, emptyMap())
                throw e
            }
        }

        // Standings only, sorted by the engine's tiebreaker order.
        get("/tournaments/{id}/standings") {
            val id = call.parameters["id"]!!
            try {
                val standings = io { tournaments.standings(id) }
                logSdkCall(sdkLogs, id, "GET_STANDINGS", null, null, null, null)
                call.respond(standings)
            } catch (e: Throwable) {
                logSdkCall(sdkLogs, id, "GET_STANDINGS", null, null, e, emptyMap())
                throw e
            }
        }

        // A user's cumulative ELO rating in this project (default 1200 if they have none).
        get("/ratings/{userId}") {
            val userId = call.parameters["userId"]!!
            val rating = io { ratings.get(call.projectId, userId, EloCalculator.DEFAULT_RATING) }
            call.respond(RatingView(userId, rating))
        }
    }
}

// Records one SDK-call log entry; wrapped in runCatching so a logging failure never breaks the request.
// On failure it also stores the request payload so the developer can inspect the rejected input.
private fun logSdkCall(
    sdkLogs: SdkLogRepository,
    tournamentId: String,
    action: String,
    userId: String?,
    matchId: String?,
    error: Throwable?,
    payload: Map<String, Any?>?
) {
    runCatching {
        val entry = mutableMapOf<String, Any?>(
            "action" to action,
            "outcome" to if (error == null) "SUCCESS" else "FAILURE",
            "timestamp" to System.currentTimeMillis()
        )
        if (userId != null) entry["userId"] = userId
        if (matchId != null) entry["matchId"] = matchId
        if (error != null) {
            entry["errorCode"] = (error as? TKException)?.code?.name ?: TKErrorCode.TK_UNKNOWN.name
            entry["errorMessage"] = error.message ?: error.javaClass.simpleName
            if (payload != null) entry["payload"] = payload
        }
        sdkLogs.append(tournamentId, entry)
    }
}

// Runs a blocking Firestore call off the Netty event loop.
private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
