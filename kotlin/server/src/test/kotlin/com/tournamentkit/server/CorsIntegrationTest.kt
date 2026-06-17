package com.tournamentkit.server

import com.tournamentkit.server.config.installCors
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Test

class CorsIntegrationTest {

    @Test
    fun options_preflight_to_any_route_returns_cors_headers() = testApplication {
        application {
            // We only install the plugins needed to test CORS behavior, avoiding the full 
            // module() which tries to connect to Firestore.
            install(ContentNegotiation) { json() }
            installCors()
            routing {
                get("/portal/projects") { call.respond("OK") }
            }
        }
        
        // Simulate a browser preflight (OPTIONS) from a permitted origin.
        val response = client.options("/portal/projects") {
            header(HttpHeaders.Origin, "http://localhost:5173")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Content-Type, Authorization, X-TK-API-KEY, X-TK-PROJECT-ID")
        }

        // Ktor's CORS plugin handles OPTIONS and returns 200 OK or 204 No Content.
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("http://localhost:5173", response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("true", response.headers[HttpHeaders.AccessControlAllowCredentials])
        
        // Check that allowed headers are returned in the preflight response.
        val allowHeaders = response.headers[HttpHeaders.AccessControlAllowHeaders]
        assert(allowHeaders?.contains("Authorization") == true)
        assert(allowHeaders?.contains("Content-Type") == true)
        assert(allowHeaders?.contains("X-TK-API-KEY") == true)
        assert(allowHeaders?.contains("X-TK-PROJECT-ID") == true)
    }

    @Test
    fun options_preflight_from_unknown_origin_is_ignored() = testApplication {
        application {
            installCors()
            routing {
                get("/portal/projects") { call.respond("OK") }
            }
        }
        val response = client.options("/portal/projects") {
            header(HttpHeaders.Origin, "http://evil.com")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }

        // CORS headers should NOT be present if the origin is not allowed.
        assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
