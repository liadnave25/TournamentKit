package com.tournamentkit.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    private val window = 60_000L

    // ---- pure decision function ----

    @Test
    fun first_request_starts_a_window_and_is_allowed() {
        val d = decideRate(state = null, now = 1000L, limit = 3, windowMillis = window)
        assertTrue(d.allowed)
        assertEquals(WindowState(1000L, 1), d.next)
    }

    @Test
    fun requests_under_the_limit_are_allowed_and_counted() {
        val d = decideRate(WindowState(1000L, 2), now = 1500L, limit = 3, windowMillis = window)
        assertTrue(d.allowed)
        assertEquals(3, d.next.count)
    }

    @Test
    fun request_at_the_limit_is_blocked_and_state_unchanged() {
        val state = WindowState(1000L, 3)
        val d = decideRate(state, now = 1500L, limit = 3, windowMillis = window)
        assertFalse(d.allowed)
        assertEquals(state, d.next) // blocked requests don't inflate the count
    }

    @Test
    fun window_resets_after_it_elapses() {
        // 3 used in the old window, but `now` is past windowMillis -> fresh window, allowed.
        val d = decideRate(WindowState(1000L, 3), now = 1000L + window, limit = 3, windowMillis = window)
        assertTrue(d.allowed)
        assertEquals(WindowState(1000L + window, 1), d.next)
    }

    // ---- the in-memory limiter ----

    @Test
    fun limiter_blocks_after_the_limit_then_allows_in_next_window() {
        val limiter = RateLimiter(limit = 2, windowMillis = window)
        val t0 = 10_000L
        assertTrue(limiter.allow("keyA", t0))       // 1
        assertTrue(limiter.allow("keyA", t0 + 1))   // 2
        assertFalse(limiter.allow("keyA", t0 + 2))  // 3 -> blocked
        // After the window passes, the same key is allowed again.
        assertTrue(limiter.allow("keyA", t0 + window))
    }

    @Test
    fun limiter_tracks_keys_independently() {
        val limiter = RateLimiter(limit = 1, windowMillis = window)
        val t0 = 10_000L
        assertTrue(limiter.allow("keyA", t0))
        assertFalse(limiter.allow("keyA", t0 + 1)) // keyA exhausted
        assertTrue(limiter.allow("keyB", t0 + 1))  // keyB has its own bucket
    }
}
