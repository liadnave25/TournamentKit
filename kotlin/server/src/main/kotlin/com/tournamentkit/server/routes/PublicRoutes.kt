package com.tournamentkit.server.routes

import com.tournamentkit.server.auth.apiKeyAuthPlugin
import com.tournamentkit.server.auth.projectId
import com.tournamentkit.server.auth.rateLimitPlugin
import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.data.RatingRepository
import com.tournamentkit.server.engine.EloCalculator
import com.tournamentkit.server.service.RateLimiter
import com.tournamentkit.server.service.ReportService
import com.tournamentkit.server.service.TournamentService
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

// Registers all authenticated /v1 endpoints. The auth plugin runs first for every route here.
fun Route.publicRoutes(
    projects: ProjectRepository,
    ratings: RatingRepository,
    tournaments: TournamentService,
    reports: ReportService,
    rateLimiter: RateLimiter
) {
    route("/v1") {
        // Rate-limit per API key (or IP) BEFORE auth, so abusive traffic is throttled either way.
        install(rateLimitPlugin(rateLimiter))
        // Every /v1 request must carry a valid API key + project id.
        install(apiKeyAuthPlugin(projects))

        // Create a tournament from a template (creator auto-joins).
        post("/tournaments") {
            val body = call.receive<CreateTournamentRequest>()
            val t = io { tournaments.create(call.projectId, body.templateId, body.name, body.userId, body.displayName) }
            call.respond(t)
        }

        // Join a tournament by its join code.
        post("/tournaments/join") {
            val body = call.receive<JoinRequest>()
            val t = io { tournaments.join(call.projectId, body.joinCode, body.userId, body.displayName) }
            call.respond(t)
        }

        // Start a tournament (creator only): lock registration and draw the matches.
        post("/tournaments/{id}/start") {
            val id = call.parameters["id"]!!
            val body = call.receive<StartRequest>()
            val t = io { tournaments.start(id, body.userId) }
            call.respond(t)
        }

        // Report a match result (the heart of the system — runs the progression transaction).
        post("/matches/report") {
            val body = call.receive<ReportRequest>()
            io { reports.report(call.projectId, body.tournamentId, body.matchId, body.userId, body.score) }
            call.respond(io { tournaments.view(body.tournamentId) })
        }

        // Confirm a previously reported result (the other player).
        post("/matches/confirm") {
            val body = call.receive<ConfirmRequest>()
            io { reports.confirm(call.projectId, body.tournamentId, body.matchId, body.userId) }
            call.respond(io { tournaments.view(body.tournamentId) })
        }

        // Full tournament view: tournament + matches + standings.
        get("/tournaments/{id}") {
            val id = call.parameters["id"]!!
            call.respond(io { tournaments.view(id) })
        }

        // Standings only, sorted by the engine's tiebreaker order.
        get("/tournaments/{id}/standings") {
            val id = call.parameters["id"]!!
            call.respond(io { tournaments.standings(id) })
        }

        // A user's cumulative ELO rating in this project (default 1200 if they have none).
        get("/ratings/{userId}") {
            val userId = call.parameters["userId"]!!
            val rating = io { ratings.get(call.projectId, userId, EloCalculator.DEFAULT_RATING) }
            call.respond(RatingView(userId, rating))
        }
    }
}

// Runs a blocking Firestore call off the Netty event loop.
private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
