package com.tournamentkit.server.service

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinCodeTest {

    @Test
    fun code_has_the_expected_length() {
        assertEquals(JoinCode.LENGTH, JoinCode.generate(Random(1)).length)
    }

    @Test
    fun code_uses_only_the_unambiguous_alphabet() {
        // No 0, O, 1, or I should ever appear.
        val banned = setOf('0', 'O', '1', 'I')
        repeat(500) {
            val code = JoinCode.generate(Random(it))
            assertTrue("code $code contains a banned character", code.none { c -> c in banned })
        }
    }

    @Test
    fun codes_are_mostly_unique_over_many_draws() {
        // 1000 codes from distinct seeds should almost never collide (32^6 space).
        val codes = (0 until 1000).map { JoinCode.generate(Random(it.toLong())) }.toSet()
        assertTrue("expected near-unique codes, got ${codes.size}/1000", codes.size > 990)
    }

    @Test
    fun same_seed_gives_same_code() {
        assertEquals(JoinCode.generate(Random(42)), JoinCode.generate(Random(42)))
    }
}
