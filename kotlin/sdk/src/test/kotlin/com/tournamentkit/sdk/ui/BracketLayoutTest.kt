package com.tournamentkit.sdk.ui

import com.tournamentkit.sdk.ui.preview.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BracketLayoutTest {

    // Simple metrics so positions are easy to reason about in assertions.
    private val metrics = BracketMetrics(cardWidth = 100f, cardHeight = 40f, hGap = 50f, vGap = 20f)

    @Test
    fun empty_matches_produce_empty_layout() {
        val layout = computeBracketLayout(emptyList(), metrics)
        assertTrue(layout.positions.isEmpty())
        assertTrue(layout.connectors.isEmpty())
        assertEquals(0f, layout.width, 0.001f)
    }

    @Test
    fun rounds_map_to_columns_by_x() {
        val layout = computeBracketLayout(SampleData.bracket(8), metrics)
        val x1 = layout.positions.getValue("r1-s0").x
        val x2 = layout.positions.getValue("r2-s0").x
        val x3 = layout.positions.getValue("r3-s0").x
        // Each round is one (cardWidth + hGap) further right.
        assertEquals(0f, x1, 0.001f)
        assertEquals(150f, x2, 0.001f)
        assertEquals(300f, x3, 0.001f)
    }

    @Test
    fun parent_y_is_the_mean_of_its_two_children_4players() {
        val layout = computeBracketLayout(SampleData.bracket(4), metrics)
        val s0 = layout.positions.getValue("r1-s0").y
        val s1 = layout.positions.getValue("r1-s1").y
        val final = layout.positions.getValue("r2-s0").y
        assertEquals((s0 + s1) / 2f, final, 0.001f)
    }

    @Test
    fun parent_centering_holds_at_every_level_8players() {
        val layout = computeBracketLayout(SampleData.bracket(8), metrics)
        // Each semifinal is centered between its two quarterfinal children.
        val semi0 = layout.positions.getValue("r2-s0").y
        assertEquals(
            (layout.positions.getValue("r1-s0").y + layout.positions.getValue("r1-s1").y) / 2f,
            semi0, 0.001f
        )
        val semi1 = layout.positions.getValue("r2-s1").y
        assertEquals(
            (layout.positions.getValue("r1-s2").y + layout.positions.getValue("r1-s3").y) / 2f,
            semi1, 0.001f
        )
        // The final is centered between the two semifinals.
        val final = layout.positions.getValue("r3-s0").y
        assertEquals((semi0 + semi1) / 2f, final, 0.001f)
    }

    @Test
    fun every_match_with_a_next_gets_a_connector() {
        val matches = SampleData.bracket(8)
        val layout = computeBracketLayout(matches, metrics)
        val expected = matches.count { it.nextMatchId != null }
        assertEquals(expected, layout.connectors.size)
        // Connectors point at real matches.
        val ids = matches.map { it.id }.toSet()
        assertTrue(layout.connectors.all { it.toId in ids && it.fromId in ids })
    }

    @Test
    fun bye_and_tbd_slots_are_still_placed() {
        val matches = SampleData.bracketWithByes()
        val layout = computeBracketLayout(matches, metrics)
        // All 7 matches (including the 3 byes and the TBD final) get a position.
        assertEquals(matches.size, layout.positions.size)
        // The bye matches (round 1) have concrete y positions.
        assertTrue(layout.positions.containsKey("r1-s1"))   // a bye
        assertTrue(layout.positions.containsKey("r3-s0"))   // the TBD final
    }
}
