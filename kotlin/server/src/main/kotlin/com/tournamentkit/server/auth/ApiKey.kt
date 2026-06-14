package com.tournamentkit.server.auth

import java.security.MessageDigest

// Hashing for API keys. We store only the SHA-256 hash of a key, never the key itself.
object ApiKey {

    // Returns the lowercase hex SHA-256 hash of the given api key.
    fun sha256(apiKey: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(apiKey.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
