package com.tournamentkit.server.service

import com.tournamentkit.shared.Scoring
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TemplateType
import org.junit.Assert.fail
import org.junit.Test

class TemplateValidationTest {

    private fun template(
        win: Int = 3, draw: Int = 1, loss: Int = 0,
        maxParticipants: Int = 8, reportTimeoutHours: Int = 48
    ) = Template("t", TemplateType.LEAGUE, Scoring(win, draw, loss), maxParticipants, false, reportTimeoutHours)

    @Test
    fun valid_template_passes() {
        validateTemplate(template())   // should not throw
    }

    @Test
    fun negative_scoring_is_rejected() {
        expectInvalid { validateTemplate(template(win = -1)) }
        expectInvalid { validateTemplate(template(draw = -1)) }
        expectInvalid { validateTemplate(template(loss = -1)) }
    }

    @Test
    fun too_few_participants_is_rejected() {
        expectInvalid { validateTemplate(template(maxParticipants = 1)) }
    }

    @Test
    fun negative_report_window_is_rejected() {
        expectInvalid { validateTemplate(template(reportTimeoutHours = -5)) }
    }

    private fun expectInvalid(block: () -> Unit) {
        try { block(); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }
}
