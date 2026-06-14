package com.tournamentkit.server.auth

import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.server.engine.TKException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.util.AttributeKey

// Call attribute holding the authenticated projectId, set by the auth plugin and read by routes.
val PROJECT_ID_KEY = AttributeKey<String>("projectId")

// Convenience accessor: the authenticated project id for this request.
val ApplicationCall.projectId: String get() = attributes[PROJECT_ID_KEY]

// Ktor plugin that authenticates every request in its route group by API key + project id headers.
fun apiKeyAuthPlugin(projects: ProjectRepository) = createRouteScopedPlugin("ApiKeyAuth") {
    onCall { call ->
        // Both identifying headers are required.
        val apiKey = call.request.headers["X-TK-API-KEY"]
            ?: throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "missing X-TK-API-KEY header")
        val projectId = call.request.headers["X-TK-PROJECT-ID"]
            ?: throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "missing X-TK-PROJECT-ID header")

        // The presented key must hash to the project's stored hash.
        val storedHash = projects.apiKeyHash(projectId)
            ?: throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "unknown project")
        if (ApiKey.sha256(apiKey) != storedHash) {
            throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "invalid api key")
        }

        // Authenticated: expose the project id to downstream routes.
        call.attributes.put(PROJECT_ID_KEY, projectId)
    }
}
