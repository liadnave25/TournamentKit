package com.tournamentkit.server.routes

import com.tournamentkit.server.config.Env
import com.tournamentkit.server.service.DevSeedService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Registers the dev-only seeding route. Returns 404 unless DEV_MODE=true.
fun Route.devRoutes(seed: DevSeedService) {
    post("/dev/seed") {
        // Hidden in production: behaves as if the route does not exist.
        if (!Env.devMode) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        // Optional body { "ownerUid": "..." } — parsed leniently so an empty body also works.
        val raw = call.receiveText()
        val ownerUid = runCatching {
            Json.parseToJsonElement(raw).jsonObject["ownerUid"]?.jsonPrimitive?.content
        }.getOrNull()
        val response = withContext(Dispatchers.IO) { seed.seed(ownerUid) }
        call.respond(response)
    }
}
