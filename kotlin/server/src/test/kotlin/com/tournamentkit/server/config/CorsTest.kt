package com.tournamentkit.server.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorsTest {

    @Test
    fun parses_scheme_and_host_with_port() {
        // A full dev origin splits into (scheme, host:port) — what Ktor's allowHost expects.
        assertEquals("http" to "localhost:5173", parseOrigin("http://localhost:5173"))
        assertEquals("https" to "portal.example.com", parseOrigin("https://portal.example.com"))
    }

    @Test
    fun host_without_scheme_yields_null_scheme() {
        val (scheme, host) = parseOrigin("localhost:5173")
        assertNull(scheme)
        assertEquals("localhost:5173", host)
    }
}
