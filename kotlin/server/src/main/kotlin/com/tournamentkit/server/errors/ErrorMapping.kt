package com.tournamentkit.server.errors

import com.tournamentkit.server.engine.TKException
import com.tournamentkit.shared.TKError
import com.tournamentkit.shared.TKErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

// Maps each error code to the HTTP status the API returns for it.
private fun statusFor(code: TKErrorCode): HttpStatusCode = when (code) {
    TKErrorCode.TK_NOT_AUTHENTICATED -> HttpStatusCode.Unauthorized          // 401
    TKErrorCode.TK_NOT_PARTICIPANT -> HttpStatusCode.Unauthorized            // 401 (acting user not in match)
    TKErrorCode.TK_TOURNAMENT_NOT_FOUND -> HttpStatusCode.NotFound           // 404
    TKErrorCode.TK_MATCH_ALREADY_REPORTED -> HttpStatusCode.Conflict         // 409
    TKErrorCode.TK_TOURNAMENT_FULL -> HttpStatusCode.Conflict                // 409
    TKErrorCode.TK_ALREADY_JOINED -> HttpStatusCode.Conflict                 // 409
    TKErrorCode.TK_TOURNAMENT_LOCKED -> HttpStatusCode.Conflict              // 409
    TKErrorCode.TK_TOURNAMENT_FROZEN -> HttpStatusCode.Conflict              // 409 (portal froze it)
    TKErrorCode.TK_FORBIDDEN -> HttpStatusCode.Forbidden                     // 403 (not the project owner)
    TKErrorCode.TK_RATE_LIMITED -> HttpStatusCode.TooManyRequests            // 429 (per-key rate limit)
    TKErrorCode.TK_INVALID_SCORE -> HttpStatusCode.BadRequest                // 400
    TKErrorCode.TK_NOT_SUPPORTED_FOR_TYPE -> HttpStatusCode.BadRequest       // 400 (op wrong for this type)
    else -> HttpStatusCode.InternalServerError                              // 500 fallback
}

// Installs one central handler so routes never need their own try/catch blocks.
fun Application.installErrorHandling() {
    install(StatusPages) {
        // Typed engine/server errors map to their documented status + JSON body.
        exception<TKException> { call, e ->
            call.respond(statusFor(e.code), TKError(e.code, e.message ?: e.code.name))
        }
        // Engine validation (require/IllegalArgument) is a client mistake -> 400.
        exception<IllegalArgumentException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, TKError(TKErrorCode.TK_INVALID_SCORE, e.message ?: "invalid request"))
        }
        // Anything unexpected -> 500 with a generic code (details stay server-side).
        exception<Throwable> { call, e ->
            call.respond(HttpStatusCode.InternalServerError, TKError(TKErrorCode.TK_UNKNOWN, e.message ?: "unexpected error"))
        }
    }
}
