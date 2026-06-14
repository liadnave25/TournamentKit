package com.tournamentkit.server.auth

import com.tournamentkit.server.service.RateLimiter
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.shared.TKErrorCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.origin

// Ktor plugin that rate-limits a route group per API key (falling back to client IP when there is no
// key). On exceed it throws TK_RATE_LIMITED, which the central handler maps to HTTP 429.
fun rateLimitPlugin(limiter: RateLimiter) = createRouteScopedPlugin("RateLimit") {
    onCall { call ->
        // Prefer the API key so a single key can't be bypassed by changing IP; else key by IP.
        val key = call.request.headers["X-TK-API-KEY"]
            ?: "ip:${call.request.origin.remoteHost}"
        if (!limiter.allow(key)) {
            throw TKException(TKErrorCode.TK_RATE_LIMITED, "rate limit exceeded — try again shortly")
        }
    }
}
