package com.tournamentkit.server.service

import com.tournamentkit.shared.Template

// Pure validation of a template's fields, throwing IllegalArgumentException (mapped to 400) on any bad value.
fun validateTemplate(t: Template) {
    // Scoring values must be non-negative (the enum already guarantees a valid type).
    require(t.scoring.win >= 0) { "scoring.win must be >= 0" }
    require(t.scoring.draw >= 0) { "scoring.draw must be >= 0" }
    require(t.scoring.loss >= 0) { "scoring.loss must be >= 0" }
    // A tournament needs at least two players.
    require(t.maxParticipants >= 2) { "maxParticipants must be >= 2" }
}
