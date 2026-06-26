package com.tournamentkit.server

import com.tournamentkit.server.config.Env
import com.tournamentkit.server.config.installCors
import com.tournamentkit.server.data.AuditRepository
import com.tournamentkit.server.data.CountersRepository
import com.tournamentkit.server.data.FirestoreProvider
import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.data.RatingRepository
import com.tournamentkit.server.data.TournamentRepository
import com.tournamentkit.server.errors.installErrorHandling
import com.tournamentkit.server.routes.devRoutes
import com.tournamentkit.server.routes.portalRoutes
import com.tournamentkit.server.routes.publicRoutes
import com.tournamentkit.server.service.DevSeedService
import com.tournamentkit.server.service.PortalService
import com.tournamentkit.server.service.RateLimiter
import com.tournamentkit.server.service.ReportService
import com.tournamentkit.server.service.TallyService
import com.tournamentkit.server.service.TournamentService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

// Boots the Ktor server; port comes from the PORT env var (Cloud Run) or defaults to 8080.
fun main() {
    embeddedServer(Netty, port = Env.port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// Wires plugins, builds the service layer over Firestore, and registers all routes.
fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    // CORS first, so browser preflight (OPTIONS) is answered for every route, including /portal and /v1.
    installCors()
    installErrorHandling()

    // Build the data + service layers once, sharing the single Firestore client.
    val db = FirestoreProvider.firestore
    val projects = ProjectRepository(db)
    val tournamentRepo = TournamentRepository(db)
    val ratings = RatingRepository(db)
    val auditRepo = AuditRepository(db)
    val countersRepo = CountersRepository(db)
    val tournamentService = TournamentService(projects, tournamentRepo, countersRepo)
    val reportService = ReportService(db, countersRepo)
    val tallyService = TallyService(db)
    val portalService = PortalService(projects, tournamentRepo, auditRepo, reportService, tournamentService, countersRepo)
    val devSeed = DevSeedService(projects)

    // One in-memory rate limiter shared across /v1 requests (per API key / IP).
    val rateLimiter = RateLimiter(limit = Env.rateLimitPerMin)

    routing {
        // Liveness check: returns 200 OK so we know the server is up.
        get("/health") { call.respondText("OK") }

        // Authenticated public API (API key), rate-limited per key/IP.
        publicRoutes(projects, ratings, tournamentService, reportService, tallyService, rateLimiter)

        // Portal management API (Firebase ID token + project ownership).
        portalRoutes(projects, portalService, tournamentService)

        // Dev-only seeding (guarded by DEV_MODE inside the route).
        devRoutes(devSeed)
    }
}
