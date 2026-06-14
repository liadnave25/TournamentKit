package com.tournamentkit.server.config

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

// Splits an origin URL into (scheme, host[:port]) for Ktor's allowHost, or (null, host) if no scheme.
// Pure + internal so it can be unit-tested without booting a server.
internal fun parseOrigin(origin: String): Pair<String?, String> {
    val parts = origin.split("://", limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else null to origin
}

// Enables CORS so browser clients (the React portal) can call /portal/* and /v1/*.
// Allowed origins come from CORS_ORIGINS (comma-separated), defaulting to the localhost dev origins.
fun Application.installCors() {
    install(CORS) {
        // Allow each configured origin (parsed into scheme + host[:port], which is what Ktor expects).
        for (origin in Env.corsOrigins) {
            val (scheme, host) = parseOrigin(origin)
            if (scheme != null) allowHost(host, schemes = listOf(scheme)) else allowHost(host)
        }

        // Methods the portal/SDK use, plus OPTIONS for preflight.
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        // Headers the clients send (Bearer token, JSON content type, and the SDK's API-key headers).
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-TK-API-KEY")
        allowHeader("X-TK-PROJECT-ID")

        // Needed for the Authorization: Bearer flow from the browser.
        allowCredentials = true
    }
}
