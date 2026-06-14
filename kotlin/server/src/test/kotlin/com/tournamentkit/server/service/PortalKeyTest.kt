package com.tournamentkit.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalKeyTest {

    @Test
    fun key_is_long_enough() {
        // At least 32 chars total (prefix + base64 body).
        assertTrue(PortalKey.generate().length >= 32)
    }

    @Test
    fun key_is_url_safe_with_known_prefix() {
        val key = PortalKey.generate()
        assertTrue("expected tk_ prefix, got $key", key.startsWith("tk_"))
        // Body uses only url-safe base64 chars (A-Z a-z 0-9 - _).
        val body = key.removePrefix("tk_")
        assertTrue(body.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun keys_are_unique_across_many_draws() {
        val keys = (0 until 1000).map { PortalKey.generate() }.toSet()
        assertEquals("all keys should be unique", 1000, keys.size)
    }
}
