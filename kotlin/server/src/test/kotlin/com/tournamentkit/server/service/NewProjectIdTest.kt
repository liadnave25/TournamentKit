package com.tournamentkit.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewProjectIdTest {

    @Test
    fun has_the_expected_prefix_and_length() {
        val id = newProjectId()
        assertTrue("expected proj- prefix, got $id", id.startsWith("proj-"))
        assertEquals("proj- plus 8 hex chars", "proj-".length + 8, id.length)
    }

    @Test
    fun ids_are_unique_across_many_draws() {
        val ids = (0 until 1000).map { newProjectId() }.toSet()
        assertEquals("all generated ids should be unique", 1000, ids.size)
    }
}
