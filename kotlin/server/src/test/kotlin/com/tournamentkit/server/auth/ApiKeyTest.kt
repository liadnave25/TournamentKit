package com.tournamentkit.server.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiKeyTest {

    @Test
    fun hash_is_stable_for_the_same_key() {
        assertEquals(ApiKey.sha256("dev-key"), ApiKey.sha256("dev-key"))
    }

    @Test
    fun different_keys_hash_differently() {
        assertNotEquals(ApiKey.sha256("dev-key"), ApiKey.sha256("other-key"))
    }

    @Test
    fun hash_matches_known_sha256_vector() {
        // Well-known SHA-256 of the empty string — proves the algorithm + hex encoding are correct.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ApiKey.sha256("")
        )
    }

    @Test
    fun hash_is_64_lowercase_hex_chars() {
        val h = ApiKey.sha256("dev-key")
        assertEquals(64, h.length)
        assertEquals(true, h.all { it in "0123456789abcdef" })
    }
}
