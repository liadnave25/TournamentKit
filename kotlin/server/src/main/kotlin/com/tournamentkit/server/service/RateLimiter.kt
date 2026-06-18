package com.tournamentkit.server.service

import java.util.concurrent.ConcurrentHashMap

// One caller's window: when the current minute started and how many requests it has made in it.
data class WindowState(val windowStartMillis: Long, val count: Int)

// The result of a rate-limit check: whether to allow the request, and the window state to store next.
data class RateDecision(val allowed: Boolean, val next: WindowState)

// Pure fixed-window decision — no clock, no I/O, so it unit-tests directly.
// Resets the count when `now` is past the window; otherwise allows until `limit` is reached.
fun decideRate(state: WindowState?, now: Long, limit: Int, windowMillis: Long): RateDecision {
    // New caller, or the previous window has elapsed -> start a fresh window with this request counted.
    if (state == null || now - state.windowStartMillis >= windowMillis) {
        return RateDecision(allowed = true, next = WindowState(now, 1))
    }
    // Within the window but at/over the limit -> block; keep the window unchanged.
    if (state.count >= limit) {
        return RateDecision(allowed = false, next = state)
    }
    // Within the window and under the limit -> allow and increment.
    return RateDecision(allowed = true, next = state.copy(count = state.count + 1))
}

// In-memory, single-instance fixed-window rate limiter keyed by API key (or client IP).
class RateLimiter(private val limit: Int, private val windowMillis: Long = 60_000L) {
    private val windows = ConcurrentHashMap<String, WindowState>()

    // Records one request for `key` and returns true if it is allowed under the current window.
    fun allow(key: String, now: Long = System.currentTimeMillis()): Boolean {
        // compute() is atomic per key, so concurrent requests for the same key can't race the counter.
        var allowed = false
        windows.compute(key) { _, existing ->
            val decision = decideRate(existing, now, limit, windowMillis)
            allowed = decision.allowed
            decision.next
        }
        return allowed
    }
}
