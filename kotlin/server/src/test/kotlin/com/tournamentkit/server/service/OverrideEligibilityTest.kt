package com.tournamentkit.server.service

import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.TemplateType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideEligibilityTest {

    @Test
    fun pending_or_reported_is_always_overridable() {
        assertTrue(overrideAllowed(MatchStatus.PENDING, TemplateType.KNOCKOUT, hasNextMatch = true, downstreamStatus = MatchStatus.PENDING))
        assertTrue(overrideAllowed(MatchStatus.REPORTED, TemplateType.KNOCKOUT, hasNextMatch = true, downstreamStatus = MatchStatus.PENDING))
    }

    @Test
    fun confirmed_league_is_always_overridable() {
        // Standings are a pure recompute, so any confirmed league/groups match can be corrected.
        assertTrue(overrideAllowed(MatchStatus.CONFIRMED, TemplateType.LEAGUE, hasNextMatch = false, downstreamStatus = null))
        assertTrue(overrideAllowed(MatchStatus.CONFIRMED, TemplateType.GROUPS_KNOCKOUT, hasNextMatch = false, downstreamStatus = null))
    }

    @Test
    fun confirmed_knockout_final_is_overridable() {
        // The final has no downstream match, so correcting it is safe.
        assertTrue(overrideAllowed(MatchStatus.CONFIRMED, TemplateType.KNOCKOUT, hasNextMatch = false, downstreamStatus = null))
    }

    @Test
    fun confirmed_knockout_with_unplayed_downstream_is_overridable() {
        assertTrue(overrideAllowed(MatchStatus.CONFIRMED, TemplateType.KNOCKOUT, hasNextMatch = true, downstreamStatus = MatchStatus.PENDING))
    }

    @Test
    fun confirmed_knockout_with_played_downstream_is_refused() {
        // The winner already advanced and played on — no cascading rollback.
        assertFalse(overrideAllowed(MatchStatus.CONFIRMED, TemplateType.KNOCKOUT, hasNextMatch = true, downstreamStatus = MatchStatus.REPORTED))
        assertFalse(overrideAllowed(MatchStatus.CONFIRMED, TemplateType.KNOCKOUT, hasNextMatch = true, downstreamStatus = MatchStatus.CONFIRMED))
    }
}
