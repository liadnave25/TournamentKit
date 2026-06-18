package com.tournamentkit.server.service

import java.security.SecureRandom
import java.util.Base64

// Generates random API keys for projects, never stored in plaintext (only their hash).
object PortalKey {
    private val secureRandom = SecureRandom()

    // Number of random bytes; Base64-url of 24 bytes yields a 32-char url-safe key.
    private const val BYTE_LENGTH = 24

    // Returns a new url-safe random key, prefixed so it is recognizable as a TournamentKit key.
    fun generate(): String {
        val bytes = ByteArray(BYTE_LENGTH).also { secureRandom.nextBytes(it) }
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "tk_$body"
    }
}
